package ru.mescat.message.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import ru.mescat.message.config.StorageProperties;
import ru.mescat.message.dto.FileDownloadUrlDto;
import ru.mescat.message.dto.FileDto;
import ru.mescat.message.dto.FileUploadInitRequestDto;
import ru.mescat.message.dto.FileUploadInitResponseDto;
import ru.mescat.message.entity.ChatEntity;
import ru.mescat.message.entity.FileEntity;
import ru.mescat.message.entity.enums.FileStatus;
import ru.mescat.message.entity.enums.FileType;
import ru.mescat.message.exception.AccessDeniedException;
import ru.mescat.message.exception.ChatNotFoundException;
import ru.mescat.message.exception.NotFoundException;
import ru.mescat.message.event.dto.FileReady;
import ru.mescat.message.repository.FileRepository;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class ChatFileService {

    private static final String UPLOAD_PREFIX = "tmp/uploads/";
    private static final String FINAL_PREFIX = "chats/";
    private static final DateTimeFormatter AMZ_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter SHORT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Set<String> INLINE_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif",
            "video/mp4", "video/webm",
            "audio/mpeg", "audio/mp4", "audio/ogg", "audio/wav", "audio/webm"
    );

    private final FileRepository fileRepository;
    private final ChatService chatService;
    private final ChatUserService chatUserService;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties storageProperties;
    private final ApplicationEventPublisher applicationEventPublisher;

    public ChatFileService(FileRepository fileRepository,
                           ChatService chatService,
                           ChatUserService chatUserService,
                           S3Client s3Client,
                           S3Presigner s3Presigner,
                           StorageProperties storageProperties,
                           ApplicationEventPublisher applicationEventPublisher) {
        this.fileRepository = fileRepository;
        this.chatService = chatService;
        this.chatUserService = chatUserService;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.storageProperties = storageProperties;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public FileUploadInitResponseDto createUploadUrl(UUID userId, FileUploadInitRequestDto request) {
        String mimeType = validateUploadRequest(request);
        ensureUserHasAccess(request.getChatId(), userId);

        UUID fileId = UUID.randomUUID();
        String safeName = safeFileName(request.getOriginalFileName());
        String uploadObjectKey = UPLOAD_PREFIX + fileId;
        String finalObjectKey = FINAL_PREFIX + request.getChatId() + "/files/" + fileId + "/" + safeName;
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(storageProperties.getUploadUrlTtl());

        ChatEntity chat = chatService.findById(request.getChatId());
        FileEntity entity = FileEntity.builder()
                .fileId(fileId)
                .status(FileStatus.UPLOAD_URL_ISSUED)
                .senderId(userId)
                .chat(chat)
                .objectKey(finalObjectKey)
                .uploadObjectKey(uploadObjectKey)
                .uploadUrlExpiresAt(expiresAt)
                .fileType(resolveFileType(mimeType))
                .mimeType(mimeType)
                .sizeBytes(request.getSizeBytes())
                .originalFileName(safeName)
                .build();

        fileRepository.save(entity);

        String uploadUrl = createPostUploadUrl();
        Map<String, String> formFields = createPostUploadFields(uploadObjectKey, mimeType, expiresAt);
        log.info("Р В Р Р‹Р В РЎвЂўР В Р’В·Р В РўвЂР В Р’В°Р В Р вЂ¦Р В Р’В° Р В Р вЂ Р РЋР вЂљР В Р’ВµР В РЎВР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р В Р’В°Р РЋР РЏ POST-Р РЋР С“Р РЋР С“Р РЋРІР‚в„–Р В Р’В»Р В РЎвЂќР В Р’В° Р В РўвЂР В Р’В»Р РЋР РЏ Р В Р’В·Р В Р’В°Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В Р’В·Р В РЎвЂќР В РЎвЂ Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В Р’В°: fileId={}, chatId={}, userId={}", fileId, request.getChatId(), userId);

        return FileUploadInitResponseDto.builder()
                .fileId(fileId)
                .chatId(request.getChatId())
                .uploadUrl(uploadUrl)
                .formFields(formFields)
                .uploadUrlExpiresAt(expiresAt)
                .method("POST")
                .build();
    }

    @Transactional
    public FileDto completeUpload(UUID userId, UUID fileId) {
        FileEntity file = fileRepository.findById(fileId)
                .orElseThrow(() -> new NotFoundException("Р В Р’В¤Р В Р’В°Р В РІвЂћвЂ“Р В Р’В» Р В Р вЂ¦Р В Р’Вµ Р В Р вЂ¦Р В Р’В°Р В РІвЂћвЂ“Р В РўвЂР В Р’ВµР В Р вЂ¦."));

        if (!userId.equals(file.getSenderId())) {
            throw new AccessDeniedException("Р В РІР‚вЂќР В Р’В°Р В Р вЂ Р В Р’ВµР РЋР вЂљР РЋРІвЂљВ¬Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В Р’В·Р В Р’В°Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В Р’В·Р В РЎвЂќР РЋРЎвЂњ Р В РЎВР В РЎвЂўР В Р’В¶Р В Р’ВµР РЋРІР‚С™ Р РЋРІР‚С™Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В РЎвЂќР В РЎвЂў Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂ”Р РЋР вЂљР В Р’В°Р В Р вЂ Р В РЎвЂР РЋРІР‚С™Р В Р’ВµР В Р’В»Р РЋР Р‰ Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В Р’В°.");
        }

        if (file.getStatus() != FileStatus.UPLOAD_URL_ISSUED) {
            throw new IllegalArgumentException("Р В РІР‚вЂќР В Р’В°Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В Р’В·Р В РЎвЂќР В Р’В° Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В Р’В° Р РЋРЎвЂњР В Р’В¶Р В Р’Вµ Р В Р’В·Р В Р’В°Р В Р вЂ Р В Р’ВµР РЋР вЂљР РЋРІвЂљВ¬Р В Р’ВµР В Р вЂ¦Р В Р’В° Р В РЎвЂР В Р’В»Р В РЎвЂ Р В РЎвЂўР РЋРІР‚С™Р В РЎВР В Р’ВµР В Р вЂ¦Р В Р’ВµР В Р вЂ¦Р В Р’В°.");
        }

        if (file.getUploadUrlExpiresAt() != null && file.getUploadUrlExpiresAt().isBefore(OffsetDateTime.now())) {
            expireUpload(file);
            throw new IllegalArgumentException("Р В Р Р‹Р РЋР вЂљР В РЎвЂўР В РЎвЂќ Р В РўвЂР В Р’ВµР В РІвЂћвЂ“Р РЋР С“Р РЋРІР‚С™Р В Р вЂ Р В РЎвЂР РЋР РЏ Р РЋР С“Р РЋР С“Р РЋРІР‚в„–Р В Р’В»Р В РЎвЂќР В РЎвЂ Р В Р’В·Р В Р’В°Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В Р’В·Р В РЎвЂќР В РЎвЂ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В Р’ВµР В РЎвЂќ. Р В РЎСљР РЋРЎвЂњР В Р’В¶Р В Р вЂ¦Р В РЎвЂў Р В Р’В·Р В Р’В°Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР РЋР С“Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р РЋРЎвЂњР РЋР вЂ№ Р РЋР С“Р РЋР С“Р РЋРІР‚в„–Р В Р’В»Р В РЎвЂќР РЋРЎвЂњ.");
        }

        HeadObjectResponse uploadedObject = headObject(file.getUploadObjectKey());
        if (uploadedObject.contentLength() == null || uploadedObject.contentLength() <= 0) {
            markFailed(file, "Р В РІР‚вЂќР В Р’В°Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В Р’В¶Р В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р В РІвЂћвЂ“ Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В» Р В РЎвЂ”Р РЋРЎвЂњР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР В РІвЂћвЂ“.");
            throw new IllegalArgumentException("Р В РІР‚вЂќР В Р’В°Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В Р’В¶Р В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р В РІвЂћвЂ“ Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В» Р В РЎвЂ”Р РЋРЎвЂњР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР В РІвЂћвЂ“.");
        }

        if (uploadedObject.contentLength() > storageProperties.getMaxFileSize().toBytes()) {
            deleteObjectIfExists(file.getUploadObjectKey());
            markFailed(file, "Р В Р’В¤Р В Р’В°Р В РІвЂћвЂ“Р В Р’В» Р В РЎвЂ”Р РЋР вЂљР В Р’ВµР В Р вЂ Р РЋРІР‚в„–Р РЋРІвЂљВ¬Р В Р’В°Р В Р’ВµР РЋРІР‚С™ Р В РўвЂР В РЎвЂўР В РЎвЂ”Р РЋРЎвЂњР РЋР С“Р РЋРІР‚С™Р В РЎвЂР В РЎВР РЋРІР‚в„–Р В РІвЂћвЂ“ Р РЋР вЂљР В Р’В°Р В Р’В·Р В РЎВР В Р’ВµР РЋР вЂљ.");
            throw new IllegalArgumentException("Р В Р’В¤Р В Р’В°Р В РІвЂћвЂ“Р В Р’В» Р В РЎвЂ”Р РЋР вЂљР В Р’ВµР В Р вЂ Р РЋРІР‚в„–Р РЋРІвЂљВ¬Р В Р’В°Р В Р’ВµР РЋРІР‚С™ Р В РўвЂР В РЎвЂўР В РЎвЂ”Р РЋРЎвЂњР РЋР С“Р РЋРІР‚С™Р В РЎвЂР В РЎВР РЋРІР‚в„–Р В РІвЂћвЂ“ Р РЋР вЂљР В Р’В°Р В Р’В·Р В РЎВР В Р’ВµР РЋР вЂљ.");
        }

        String uploadedContentType = uploadedObject.contentType();

        copyToFinalLocation(file);
        deleteObjectIfExists(file.getUploadObjectKey());

        file.setStatus(FileStatus.READY);
        file.setSizeBytes(uploadedObject.contentLength());
        if (StringUtils.hasText(uploadedContentType)) {
            file.setMimeType(uploadedContentType);
            file.setFileType(resolveFileType(uploadedContentType));
        }

        log.info("Р В Р’В¤Р В Р’В°Р В РІвЂћвЂ“Р В Р’В» Р РЋРЎвЂњР РЋР С“Р В РЎвЂ”Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂў Р В Р’В·Р В Р’В°Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В Р’В¶Р В Р’ВµР В Р вЂ¦ Р В РЎвЂ Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р’ВµР В Р вЂ¦Р В Р’ВµР РЋР С“Р В Р’ВµР В Р вЂ¦ Р В Р вЂ  Р В РЎвЂ”Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋР РЏР В Р вЂ¦Р В Р вЂ¦Р В РЎвЂўР В Р’Вµ Р РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’В»Р В РЎвЂР РЋРІР‚В°Р В Р’Вµ: fileId={}, chatId={}, userId={}",
                file.getFileId(), file.getChat().getChatId(), userId);
        FileDto dto = toDto(file);
        applicationEventPublisher.publishEvent(new FileReady(dto));
        return dto;
    }

    @Transactional(readOnly = true)
    public List<FileDto> getReadyFilesInChat(UUID userId, Long chatId) {
        ensureUserHasAccess(chatId, userId);
        return fileRepository.findByChat_ChatIdAndStatusOrderByCreatedAtAsc(chatId, FileStatus.READY)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public FileDownloadUrlDto createDownloadUrl(UUID userId, UUID fileId, boolean inline) {
        FileEntity file = fileRepository.findByFileIdAndStatus(fileId, FileStatus.READY)
                .orElseThrow(() -> new NotFoundException("Р В Р’В¤Р В Р’В°Р В РІвЂћвЂ“Р В Р’В» Р В Р вЂ¦Р В Р’Вµ Р В Р вЂ¦Р В Р’В°Р В РІвЂћвЂ“Р В РўвЂР В Р’ВµР В Р вЂ¦ Р В РЎвЂР В Р’В»Р В РЎвЂ Р В Р’ВµР РЋРІР‚В°Р В Р’Вµ Р В Р вЂ¦Р В Р’Вµ Р В РЎвЂ“Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂўР В Р вЂ ."));
        ensureUserHasAccess(file.getChat().getChatId(), userId);

        boolean safeInline = inline && isInlineMimeType(file.getMimeType());
        var getObjectRequest = software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                .bucket(storageProperties.getFilesBucket())
                .key(file.getObjectKey())
                .responseContentDisposition(contentDisposition(file.getOriginalFileName(), safeInline))
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(storageProperties.getDownloadUrlTtl())
                .getObjectRequest(getObjectRequest)
                .build();

        return FileDownloadUrlDto.builder()
                .fileId(fileId)
                .downloadUrl(s3Presigner.presignGetObject(presignRequest).url().toString())
                .downloadUrlExpiresAt(OffsetDateTime.now().plus(storageProperties.getDownloadUrlTtl()))
                .method("GET")
                .build();
    }

    @Transactional
    public int cleanupExpiredUploads() {
        List<FileEntity> expiredFiles = fileRepository
                .findTop100ByStatusAndUploadUrlExpiresAtBeforeOrderByUploadUrlExpiresAtAsc(
                        FileStatus.UPLOAD_URL_ISSUED,
                        OffsetDateTime.now()
                );

        for (FileEntity file : expiredFiles) {
            expireUpload(file);
        }

        if (!expiredFiles.isEmpty()) {
            log.info("Р В РЎвЂєР РЋРІР‚РЋР В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂќР В Р’В° S3: Р В РЎвЂўР В Р’В±Р РЋР вЂљР В Р’В°Р В Р’В±Р В РЎвЂўР РЋРІР‚С™Р В Р’В°Р В Р вЂ¦Р В РЎвЂў Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР РЋР С“Р РЋР вЂљР В РЎвЂўР РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р РЋРІР‚В¦ Р В Р’В·Р В Р’В°Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В Р’В·Р В РЎвЂўР В РЎвЂќ: {}", expiredFiles.size());
        }
        return expiredFiles.size();
    }

    private String validateUploadRequest(FileUploadInitRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Р В РЎС›Р В Р’ВµР В Р’В»Р В РЎвЂў Р В Р’В·Р В Р’В°Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР РЋР С“Р В Р’В° Р В Р вЂ¦Р В Р’Вµ Р В РўвЂР В РЎвЂўР В Р’В»Р В Р’В¶Р В Р вЂ¦Р В РЎвЂў Р В Р’В±Р РЋРІР‚в„–Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂ”Р РЋРЎвЂњР РЋР С“Р РЋРІР‚С™Р РЋРІР‚в„–Р В РЎВ.");
        }
        if (request.getChatId() == null) {
            throw new IllegalArgumentException("Р В РЎСљР В Р’Вµ Р РЋРЎвЂњР В РЎвЂќР В Р’В°Р В Р’В·Р В Р’В°Р В Р вЂ¦ Р РЋРІР‚РЋР В Р’В°Р РЋРІР‚С™ Р В РўвЂР В Р’В»Р РЋР РЏ Р В Р’В·Р В Р’В°Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В Р’В·Р В РЎвЂќР В РЎвЂ Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В Р’В°.");
        }
        if (!StringUtils.hasText(request.getOriginalFileName())) {
            throw new IllegalArgumentException("Р В РЎСљР В Р’Вµ Р РЋРЎвЂњР В РЎвЂќР В Р’В°Р В Р’В·Р В Р’В°Р В Р вЂ¦Р В РЎвЂў Р В РЎвЂР В РЎВР РЋР РЏ Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В Р’В°.");
        }
        String mimeType = normalizeMimeType(request.getMimeType());
        if (!StringUtils.hasText(mimeType)) {
            mimeType = "application/octet-stream";
        }
        if (request.getSizeBytes() == null || request.getSizeBytes() <= 0) {
            throw new IllegalArgumentException("Р В Р’В Р В Р’В°Р В Р’В·Р В РЎВР В Р’ВµР РЋР вЂљ Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В Р’В° Р В РўвЂР В РЎвЂўР В Р’В»Р В Р’В¶Р В Р’ВµР В Р вЂ¦ Р В Р’В±Р РЋРІР‚в„–Р РЋРІР‚С™Р РЋР Р‰ Р В Р’В±Р В РЎвЂўР В Р’В»Р РЋР Р‰Р РЋРІвЂљВ¬Р В Р’Вµ Р В Р вЂ¦Р РЋРЎвЂњР В Р’В»Р РЋР РЏ.");
        }
        if (request.getSizeBytes() > storageProperties.getMaxFileSize().toBytes()) {
            throw new IllegalArgumentException("Р В Р’В¤Р В Р’В°Р В РІвЂћвЂ“Р В Р’В» Р В РЎвЂ”Р РЋР вЂљР В Р’ВµР В Р вЂ Р РЋРІР‚в„–Р РЋРІвЂљВ¬Р В Р’В°Р В Р’ВµР РЋРІР‚С™ Р В РўвЂР В РЎвЂўР В РЎвЂ”Р РЋРЎвЂњР РЋР С“Р РЋРІР‚С™Р В РЎвЂР В РЎВР РЋРІР‚в„–Р В РІвЂћвЂ“ Р РЋР вЂљР В Р’В°Р В Р’В·Р В РЎВР В Р’ВµР РЋР вЂљ.");
        }
        return mimeType;
    }

    private void ensureUserHasAccess(Long chatId, UUID userId) {
        if (chatService.findById(chatId) == null) {
            throw new ChatNotFoundException("Р В Р’В§Р В Р’В°Р РЋРІР‚С™ Р В Р вЂ¦Р В Р’Вµ Р В Р вЂ¦Р В Р’В°Р В РІвЂћвЂ“Р В РўвЂР В Р’ВµР В Р вЂ¦.");
        }
        if (!chatUserService.existsByChatIdAndUserId(chatId, userId)) {
            throw new AccessDeniedException("Р В РЎСљР В Р’ВµР РЋРІР‚С™ Р В РўвЂР В РЎвЂўР РЋР С“Р РЋРІР‚С™Р РЋРЎвЂњР В РЎвЂ”Р В Р’В° Р В РЎвЂќ Р РЋР РЉР РЋРІР‚С™Р В РЎвЂўР В РЎВР РЋРЎвЂњ Р РЋРІР‚РЋР В Р’В°Р РЋРІР‚С™Р РЋРЎвЂњ.");
        }
    }

    private HeadObjectResponse headObject(String objectKey) {
        try {
            return s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(storageProperties.getFilesBucket())
                    .key(objectKey)
                    .build());
        } catch (NoSuchKeyException e) {
            throw new NotFoundException("Р В Р’В¤Р В Р’В°Р В РІвЂћвЂ“Р В Р’В» Р В Р вЂ¦Р В Р’Вµ Р В Р’В±Р РЋРІР‚в„–Р В Р’В» Р В Р’В·Р В Р’В°Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В Р’В¶Р В Р’ВµР В Р вЂ¦ Р В Р вЂ  S3.");
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new NotFoundException("Р В Р’В¤Р В Р’В°Р В РІвЂћвЂ“Р В Р’В» Р В Р вЂ¦Р В Р’Вµ Р В Р’В±Р РЋРІР‚в„–Р В Р’В» Р В Р’В·Р В Р’В°Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В Р’В¶Р В Р’ВµР В Р вЂ¦ Р В Р вЂ  S3.");
            }
            throw e;
        }
    }

    private void copyToFinalLocation(FileEntity file) {
        s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(storageProperties.getFilesBucket())
                .sourceKey(file.getUploadObjectKey())
                .destinationBucket(storageProperties.getFilesBucket())
                .destinationKey(file.getObjectKey())
                .contentType(file.getMimeType())
                .build());
    }

    private void expireUpload(FileEntity file) {
        deleteObjectIfExists(file.getUploadObjectKey());
        file.setStatus(FileStatus.EXPIRED);
        log.info("Р В РЎСџР РЋР вЂљР В РЎвЂўР РЋР С“Р РЋР вЂљР В РЎвЂўР РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р В Р’В°Р РЋР РЏ Р В Р’В·Р В Р’В°Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В Р’В·Р В РЎвЂќР В Р’В° Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В Р’В° Р В РЎвЂўР РЋРІР‚РЋР В РЎвЂР РЋРІР‚В°Р В Р’ВµР В Р вЂ¦Р В Р’В°: fileId={}, uploadObjectKey={}", file.getFileId(), file.getUploadObjectKey());
    }

    private void markFailed(FileEntity file, String reason) {
        file.setStatus(FileStatus.FAILED);
        log.warn("Р В РІР‚вЂќР В Р’В°Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В Р’В·Р В РЎвЂќР В Р’В° Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В Р’В° Р В РЎвЂ”Р В РЎвЂўР В РЎВР В Р’ВµР РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В Р’В° Р В РЎвЂўР РЋРІвЂљВ¬Р В РЎвЂР В Р’В±Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂўР В РІвЂћвЂ“: fileId={}, reason={}", file.getFileId(), reason);
    }

    private void deleteObjectIfExists(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(storageProperties.getFilesBucket())
                    .key(objectKey)
                    .build());
        } catch (Exception e) {
            log.warn("Р В РЎСљР В Р’Вµ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂўР РЋР С“Р РЋР Р‰ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂўР В Р’В±Р РЋР вЂ°Р В Р’ВµР В РЎвЂќР РЋРІР‚С™ Р В РЎвЂР В Р’В· S3: objectKey={}, error={}", objectKey, e.getMessage());
        }
    }

    private FileDto toDto(FileEntity file) {
        return FileDto.builder()
                .fileId(file.getFileId())
                .chatId(file.getChat().getChatId())
                .senderId(file.getSenderId())
                .status(file.getStatus())
                .createdAt(file.getCreatedAt())
                .fileType(file.getFileType())
                .mimeType(file.getMimeType())
                .sizeBytes(file.getSizeBytes())
                .originalFileName(file.getOriginalFileName())
                .build();
    }

    private FileType resolveFileType(String mimeType) {
        String normalized = normalizeMimeType(mimeType);
        if (normalized.startsWith("image/")) {
            return FileType.IMAGE;
        }
        if (normalized.startsWith("video/")) {
            return FileType.VIDEO;
        }
        if (normalized.startsWith("audio/")) {
            return FileType.AUDIO;
        }
        return FileType.FILE;
    }


    private boolean isInlineMimeType(String mimeType) {
        return INLINE_MIME_TYPES.contains(normalizeMimeType(mimeType));
    }

    private String normalizeMimeType(String mimeType) {
        if (!StringUtils.hasText(mimeType)) {
            return "";
        }
        int semicolonIndex = mimeType.indexOf(';');
        String cleanType = semicolonIndex >= 0 ? mimeType.substring(0, semicolonIndex) : mimeType;
        return cleanType.trim().toLowerCase(Locale.ROOT);
    }

    private String safeFileName(String originalFileName) {
        String cleanName = StringUtils.cleanPath(originalFileName).replace("\\", "/");
        int slashIndex = cleanName.lastIndexOf('/');
        if (slashIndex >= 0) {
            cleanName = cleanName.substring(slashIndex + 1);
        }
        cleanName = cleanName.replaceAll("[^\\p{L}\\p{N}._ -]", "_").trim();
        return StringUtils.hasText(cleanName) ? cleanName : "file";
    }

    private String contentDisposition(String fileName, boolean inline) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return (inline ? "inline" : "attachment") + "; filename*=UTF-8''" + encoded;
    }

    private String createPostUploadUrl() {
        return URI.create(storageProperties.getEndpoint())
                .resolve("/" + storageProperties.getFilesBucket())
                .toString();
    }

    private Map<String, String> createPostUploadFields(String uploadObjectKey, String mimeType, OffsetDateTime expiresAt) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String shortDate = now.format(SHORT_DATE_FORMAT);
        String amzDate = now.format(AMZ_DATE_FORMAT);
        String credential = storageProperties.getAccessKey()
                + "/" + shortDate
                + "/" + storageProperties.getRegion()
                + "/s3/aws4_request";

        String policy = createPostPolicy(uploadObjectKey, mimeType, expiresAt, credential, amzDate);
        String encodedPolicy = Base64.getEncoder().encodeToString(policy.getBytes(StandardCharsets.UTF_8));
        String signature = signPolicy(encodedPolicy, shortDate);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("key", uploadObjectKey);
        fields.put("Content-Type", mimeType);
        fields.put("x-amz-algorithm", "AWS4-HMAC-SHA256");
        fields.put("x-amz-credential", credential);
        fields.put("x-amz-date", amzDate);
        fields.put("policy", encodedPolicy);
        fields.put("x-amz-signature", signature);
        return fields;
    }

    private String createPostPolicy(String uploadObjectKey,
                                    String mimeType,
                                    OffsetDateTime expiresAt,
                                    String credential,
                                    String amzDate) {
        String expiration = expiresAt.withOffsetSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        long maxFileSize = storageProperties.getMaxFileSize().toBytes();
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
                jsonEscape(storageProperties.getFilesBucket()),
                jsonEscape(uploadObjectKey),
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
            throw new IllegalStateException("Р В РЎСљР В Р’Вµ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂўР РЋР С“Р РЋР Р‰ Р В РЎвЂ”Р В РЎвЂўР В РўвЂР В РЎвЂ”Р В РЎвЂР РЋР С“Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ S3 POST policy.", e);
        }
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


