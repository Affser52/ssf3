package ru.mescat.info.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import ru.mescat.config.StorageProperties;
import ru.mescat.info.dto.AvatarUploadCompleteResponseDto;
import ru.mescat.info.dto.AvatarUploadInitRequestDto;
import ru.mescat.info.dto.AvatarUploadInitResponseDto;
import ru.mescat.info.entity.AvatarUploadEntity;
import ru.mescat.info.entity.AvatarUploadStatus;
import ru.mescat.info.entity.UserEntity;
import ru.mescat.info.repository.AvatarUploadRepository;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.S3Exception;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class AvatarService {

    private static final String TEMP_PREFIX = "tmp/avatars/";
    private static final String FINAL_PREFIX = "avatars/";
    private static final DateTimeFormatter AMZ_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter SHORT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AvatarUploadRepository avatarUploadRepository;
    private final UserService userService;
    private final S3Client s3Client;
    private final StorageProperties storageProperties;

    public AvatarService(AvatarUploadRepository avatarUploadRepository,
                         UserService userService,
                         S3Client s3Client,
                         StorageProperties storageProperties) {
        this.avatarUploadRepository = avatarUploadRepository;
        this.userService = userService;
        this.s3Client = s3Client;
        this.storageProperties = storageProperties;
    }

    @Transactional
    public AvatarUploadInitResponseDto createUploadUrl(UUID userId, AvatarUploadInitRequestDto request) {
        validateRequest(userId, request);
        ensureUserExists(userId);

        UUID uploadId = UUID.randomUUID();
        String mimeType = normalizeMimeType(request.getMimeType());
        String extension = extensionByMimeType(mimeType);
        String tempObjectKey = TEMP_PREFIX + userId + "/" + uploadId + extension;
        String finalObjectKey = FINAL_PREFIX + userId + "/avatar" + extension;
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(storageProperties.getUploadUrlTtl());

        AvatarUploadEntity entity = AvatarUploadEntity.builder()
                .uploadId(uploadId)
                .userId(userId)
                .status(AvatarUploadStatus.UPLOAD_URL_ISSUED)
                .tempBucket(storageProperties.getAvatarTempBucket())
                .tempObjectKey(tempObjectKey)
                .finalBucket(storageProperties.getAvatarsBucket())
                .finalObjectKey(finalObjectKey)
                .originalFileName(safeFileName(request.getOriginalFileName()))
                .mimeType(mimeType)
                .sizeBytes(request.getSizeBytes())
                .uploadUrlExpiresAt(expiresAt)
                .build();

        avatarUploadRepository.save(entity);

        String uploadUrl = createPostUploadUrl(storageProperties.getAvatarTempBucket());
        Map<String, String> formFields = createPostUploadFields(
                storageProperties.getAvatarTempBucket(),
                tempObjectKey,
                mimeType,
                expiresAt,
                storageProperties.getAvatarMaxFileSize().toBytes()
        );

        log.info("Создана POST-ссылка для загрузки аватарки: userId={}, uploadId={}", userId, uploadId);
        return AvatarUploadInitResponseDto.builder()
                .uploadId(uploadId)
                .uploadUrl(uploadUrl)
                .formFields(formFields)
                .uploadUrlExpiresAt(expiresAt)
                .method("POST")
                .build();
    }

    @Transactional
    public AvatarUploadCompleteResponseDto completeUpload(UUID userId, UUID uploadId) {
        AvatarUploadEntity upload = avatarUploadRepository.findByUploadIdAndUserId(uploadId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Загрузка аватарки не найдена."));

        if (upload.getStatus() != AvatarUploadStatus.UPLOAD_URL_ISSUED) {
            throw new IllegalArgumentException("Загрузка аватарки уже завершена или отменена.");
        }

        if (upload.getUploadUrlExpiresAt() != null && upload.getUploadUrlExpiresAt().isBefore(OffsetDateTime.now())) {
            expireUpload(upload);
            throw new IllegalArgumentException("Срок действия ссылки загрузки истек. Нужно запросить новую ссылку.");
        }

        UserEntity user = userService.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден."));

        HeadObjectResponse tempObject = headObject(upload.getTempBucket(), upload.getTempObjectKey());
        validateUploadedAvatar(upload, tempObject);
        validateImageMagicBytes(upload);

        copyToPublicBucket(upload);
        deleteObjectIfExists(upload.getTempBucket(), upload.getTempObjectKey());

        String oldAvatarUrl = user.getAvatarUrl();
        String newAvatarUrl = buildPublicAvatarUrl(upload.getFinalObjectKey(), upload.getUploadId());
        boolean updated = userService.updateAvatarUrl(userId, newAvatarUrl);
        if (!updated) {
            upload.setStatus(AvatarUploadStatus.FAILED);
            throw new IllegalArgumentException("Не удалось обновить аватарку пользователя.");
        }

        deleteOldAvatarIfOwned(oldAvatarUrl, upload.getFinalObjectKey());
        upload.setStatus(AvatarUploadStatus.READY);
        log.info("Аватарка пользователя обновлена: userId={}, uploadId={}", userId, uploadId);

        return AvatarUploadCompleteResponseDto.builder()
                .userId(userId)
                .avatarUrl(newAvatarUrl)
                .build();
    }

    @Transactional
    public int cleanupExpiredUploads() {
        List<AvatarUploadEntity> expiredUploads = avatarUploadRepository
                .findTop100ByStatusAndUploadUrlExpiresAtBeforeOrderByUploadUrlExpiresAtAsc(
                        AvatarUploadStatus.UPLOAD_URL_ISSUED,
                        OffsetDateTime.now()
                );

        for (AvatarUploadEntity upload : expiredUploads) {
            expireUpload(upload);
        }

        if (!expiredUploads.isEmpty()) {
            log.info("Очистка S3: обработано просроченных загрузок аватарок: {}", expiredUploads.size());
        }
        return expiredUploads.size();
    }

    private void validateRequest(UUID userId, AvatarUploadInitRequestDto request) {
        if (userId == null) {
            throw new IllegalArgumentException("Не указан пользователь.");
        }
        if (request == null) {
            throw new IllegalArgumentException("Тело запроса не должно быть пустым.");
        }
        if (!StringUtils.hasText(request.getOriginalFileName())) {
            throw new IllegalArgumentException("Не указано имя файла.");
        }
        if (!StringUtils.hasText(request.getMimeType())) {
            throw new IllegalArgumentException("Не указан тип файла.");
        }
        String mimeType = normalizeMimeType(request.getMimeType());
        if (!isAllowedAvatarMimeType(mimeType)) {
            throw new IllegalArgumentException("Аватаркой может быть только JPG, PNG, WEBP или GIF.");
        }
        if (request.getSizeBytes() == null || request.getSizeBytes() <= 0) {
            throw new IllegalArgumentException("Размер файла должен быть больше нуля.");
        }
        if (request.getSizeBytes() > storageProperties.getAvatarMaxFileSize().toBytes()) {
            throw new IllegalArgumentException("Аватарка не должна быть больше 5 МБ.");
        }
    }

    private void ensureUserExists(UUID userId) {
        if (userService.findById(userId).isEmpty()) {
            throw new IllegalArgumentException("Пользователь не найден.");
        }
    }

    private void validateUploadedAvatar(AvatarUploadEntity upload, HeadObjectResponse tempObject) {
        if (tempObject.contentLength() == null || tempObject.contentLength() <= 0) {
            failAndDeleteTemp(upload, "Загруженная аватарка пустая.");
            throw new IllegalArgumentException("Загруженная аватарка пустая.");
        }
        if (tempObject.contentLength() > storageProperties.getAvatarMaxFileSize().toBytes()) {
            failAndDeleteTemp(upload, "Аватарка превышает допустимый размер.");
            throw new IllegalArgumentException("Аватарка превышает допустимый размер.");
        }
        String uploadedType = normalizeMimeType(tempObject.contentType());
        if (!upload.getMimeType().equals(uploadedType) || !isAllowedAvatarMimeType(uploadedType)) {
            failAndDeleteTemp(upload, "Тип аватарки не совпадает с заявленным.");
            throw new IllegalArgumentException("Тип аватарки не совпадает с заявленным.");
        }
    }

    private void validateImageMagicBytes(AvatarUploadEntity upload) {
        try {
            ResponseBytes<GetObjectResponse> bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(upload.getTempBucket())
                    .key(upload.getTempObjectKey())
                    .range("bytes=0-31")
                    .build());
            if (!matchesImageSignature(upload.getMimeType(), bytes.asByteArray())) {
                failAndDeleteTemp(upload, "Содержимое файла не похоже на изображение.");
                throw new IllegalArgumentException("Содержимое файла не похоже на изображение.");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            failAndDeleteTemp(upload, "Не удалось проверить содержимое аватарки.");
            throw new IllegalArgumentException("Не удалось проверить содержимое аватарки.");
        }
    }

    private HeadObjectResponse headObject(String bucket, String objectKey) {
        try {
            return s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
        } catch (NoSuchKeyException e) {
            throw new IllegalArgumentException("Файл не был загружен в S3.");
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new IllegalArgumentException("Файл не был загружен в S3.");
            }
            throw e;
        }
    }

    private void copyToPublicBucket(AvatarUploadEntity upload) {
        s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(upload.getTempBucket())
                .sourceKey(upload.getTempObjectKey())
                .destinationBucket(upload.getFinalBucket())
                .destinationKey(upload.getFinalObjectKey())
                .contentType(upload.getMimeType())
                .acl(ObjectCannedACL.PUBLIC_READ)
                .build());
    }

    private void expireUpload(AvatarUploadEntity upload) {
        deleteObjectIfExists(upload.getTempBucket(), upload.getTempObjectKey());
        upload.setStatus(AvatarUploadStatus.EXPIRED);
        log.info("Просроченная загрузка аватарки очищена: userId={}, uploadId={}", upload.getUserId(), upload.getUploadId());
    }

    private void failAndDeleteTemp(AvatarUploadEntity upload, String reason) {
        deleteObjectIfExists(upload.getTempBucket(), upload.getTempObjectKey());
        upload.setStatus(AvatarUploadStatus.FAILED);
        log.warn("Загрузка аватарки помечена ошибочной: userId={}, uploadId={}, reason={}",
                upload.getUserId(), upload.getUploadId(), reason);
    }

    private void deleteOldAvatarIfOwned(String oldAvatarUrl, String newFinalObjectKey) {
        extractAvatarObjectKey(oldAvatarUrl)
                .filter(oldKey -> !oldKey.equals(newFinalObjectKey))
                .ifPresent(oldKey -> deleteObjectIfExists(storageProperties.getAvatarsBucket(), oldKey));
    }

    private Optional<String> extractAvatarObjectKey(String avatarUrl) {
        if (!StringUtils.hasText(avatarUrl)) {
            return Optional.empty();
        }
        try {
            URI endpoint = URI.create(storageProperties.getEndpoint());
            URI avatarUri = URI.create(avatarUrl);
            if (!endpoint.getHost().equalsIgnoreCase(avatarUri.getHost())) {
                return Optional.empty();
            }
            String bucketPrefix = "/" + storageProperties.getAvatarsBucket() + "/";
            String path = avatarUri.getPath();
            if (path == null || !path.startsWith(bucketPrefix)) {
                return Optional.empty();
            }
            return Optional.of(path.substring(bucketPrefix.length()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private void deleteObjectIfExists(String bucket, String objectKey) {
        if (!StringUtils.hasText(bucket) || !StringUtils.hasText(objectKey)) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
        } catch (Exception e) {
            log.warn("Не удалось удалить объект из S3: bucket={}, objectKey={}, error={}", bucket, objectKey, e.getMessage());
        }
    }

    private String createPostUploadUrl(String bucket) {
        return URI.create(storageProperties.getEndpoint())
                .resolve("/" + bucket)
                .toString();
    }

    private Map<String, String> createPostUploadFields(String bucket,
                                                       String objectKey,
                                                       String mimeType,
                                                       OffsetDateTime expiresAt,
                                                       long maxFileSize) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String shortDate = now.format(SHORT_DATE_FORMAT);
        String amzDate = now.format(AMZ_DATE_FORMAT);
        String credential = storageProperties.getAccessKey()
                + "/" + shortDate
                + "/" + storageProperties.getRegion()
                + "/s3/aws4_request";

        String policy = createPostPolicy(bucket, objectKey, mimeType, expiresAt, credential, amzDate, maxFileSize);
        String encodedPolicy = Base64.getEncoder().encodeToString(policy.getBytes(StandardCharsets.UTF_8));
        String signature = signPolicy(encodedPolicy, shortDate);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("key", objectKey);
        fields.put("Content-Type", mimeType);
        fields.put("x-amz-algorithm", "AWS4-HMAC-SHA256");
        fields.put("x-amz-credential", credential);
        fields.put("x-amz-date", amzDate);
        fields.put("policy", encodedPolicy);
        fields.put("x-amz-signature", signature);
        return fields;
    }

    private String createPostPolicy(String bucket,
                                    String objectKey,
                                    String mimeType,
                                    OffsetDateTime expiresAt,
                                    String credential,
                                    String amzDate,
                                    long maxFileSize) {
        String expiration = expiresAt.withOffsetSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        return """
                {
                  "expiration": "%s",
                  "conditions": [
                    {"bucket": "%s"},
                    {"key": "%s"},
                    {"Content-Type": "%s"},
                    {"x-amz-algorithm": "AWS4-HMAC-SHA256"},
                    {"x-amz-credential": "%s"},
                    {"x-amz-date": "%s"},
                    ["content-length-range", 1, %d]
                  ]
                }
                """.formatted(
                expiration,
                jsonEscape(bucket),
                jsonEscape(objectKey),
                jsonEscape(mimeType),
                jsonEscape(credential),
                jsonEscape(amzDate),
                maxFileSize
        );
    }

    private String signPolicy(String encodedPolicy, String shortDate) {
        byte[] dateKey = hmacSha256(("AWS4" + storageProperties.getSecretKey()).getBytes(StandardCharsets.UTF_8), shortDate);
        byte[] regionKey = hmacSha256(dateKey, storageProperties.getRegion());
        byte[] serviceKey = hmacSha256(regionKey, "s3");
        byte[] signingKey = hmacSha256(serviceKey, "aws4_request");
        return toHex(hmacSha256(signingKey, encodedPolicy));
    }

    private byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось подписать S3 POST policy.", e);
        }
    }

    private String buildPublicAvatarUrl(String objectKey, UUID uploadId) {
        return URI.create(storageProperties.getEndpoint())
                .resolve("/" + storageProperties.getAvatarsBucket() + "/" + objectKey)
                .toString() + "?v=" + uploadId;
    }

    private String safeFileName(String originalFileName) {
        String cleanName = StringUtils.cleanPath(originalFileName).replace("\\", "/");
        int slashIndex = cleanName.lastIndexOf('/');
        if (slashIndex >= 0) {
            cleanName = cleanName.substring(slashIndex + 1);
        }
        cleanName = cleanName.replaceAll("[^a-zA-Z0-9а-яА-ЯёЁ._ -]", "_").trim();
        return StringUtils.hasText(cleanName) ? cleanName : "avatar";
    }

    private String normalizeMimeType(String mimeType) {
        if (!StringUtils.hasText(mimeType)) {
            return "";
        }
        int semicolonIndex = mimeType.indexOf(';');
        String cleanType = semicolonIndex >= 0 ? mimeType.substring(0, semicolonIndex) : mimeType;
        return cleanType.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isAllowedAvatarMimeType(String mimeType) {
        return "image/jpeg".equals(mimeType)
                || "image/png".equals(mimeType)
                || "image/webp".equals(mimeType)
                || "image/gif".equals(mimeType);
    }

    private String extensionByMimeType(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> throw new IllegalArgumentException("Неподдерживаемый тип аватарки.");
        };
    }

    private boolean matchesImageSignature(String mimeType, byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return false;
        }
        return switch (mimeType) {
            case "image/jpeg" -> bytes.length >= 3
                    && (bytes[0] & 0xFF) == 0xFF
                    && (bytes[1] & 0xFF) == 0xD8
                    && (bytes[2] & 0xFF) == 0xFF;
            case "image/png" -> bytes.length >= 8
                    && (bytes[0] & 0xFF) == 0x89
                    && bytes[1] == 0x50
                    && bytes[2] == 0x4E
                    && bytes[3] == 0x47
                    && bytes[4] == 0x0D
                    && bytes[5] == 0x0A
                    && bytes[6] == 0x1A
                    && bytes[7] == 0x0A;
            case "image/webp" -> bytes.length >= 12
                    && bytes[0] == 0x52
                    && bytes[1] == 0x49
                    && bytes[2] == 0x46
                    && bytes[3] == 0x46
                    && bytes[8] == 0x57
                    && bytes[9] == 0x45
                    && bytes[10] == 0x42
                    && bytes[11] == 0x50;
            case "image/gif" -> bytes.length >= 6
                    && bytes[0] == 0x47
                    && bytes[1] == 0x49
                    && bytes[2] == 0x46
                    && bytes[3] == 0x38
                    && (bytes[4] == 0x37 || bytes[4] == 0x39)
                    && bytes[5] == 0x61;
            default -> false;
        };
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
