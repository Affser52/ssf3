package ru.mescat.message.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "storage.s3")
public class StorageProperties {

    private String endpoint;
    private String region;
    private String accessKey;
    private String secretKey;
    private String avatarsBucket;
    private String filesBucket;
    private Duration uploadUrlTtl = Duration.ofMinutes(3);
    private Duration downloadUrlTtl = Duration.ofMinutes(5);
    private DataSize maxFileSize = DataSize.ofMegabytes(100);
}
