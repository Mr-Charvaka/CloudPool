package com.cloudpool.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@Slf4j
public class AwsS3Config {

    @Value("${cloudpool.aws.s3.access-key:}")
    private String accessKey;

    @Value("${cloudpool.aws.s3.secret-key:}")
    private String secretKey;

    @Value("${cloudpool.aws.s3.region:us-east-1}")
    private String region;

    @Bean
    @ConditionalOnProperty(name = "cloudpool.aws.s3.enabled", havingValue = "true")
    public S3Client s3Client() {
        log.info("Initializing Global S3 Client for Region: {}", region);
        
        if (accessKey == null || accessKey.isEmpty() || secretKey == null || secretKey.isEmpty()) {
            log.warn("S3 is enabled, but access-key or secret-key is missing. Using default AWS credential provider chain.");
            return S3Client.builder()
                    .region(Region.of(region))
                    .build();
        }

        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }
}
