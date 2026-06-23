package com.cloudpool.service;

import com.cloudpool.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "cloudpool.aws.s3.enabled", havingValue = "true")
public class S3Service {

    private final S3Client s3Client;

    @Value("${cloudpool.aws.s3.bucket:}")
    private String defaultBucketName;

    public String uploadFile(MultipartFile file, User user) throws IOException {
        if (defaultBucketName == null || defaultBucketName.isEmpty()) {
            throw new IllegalStateException("S3 bucket name is not configured.");
        }

        String fileKey = user.getId().toString() + "/" + UUID.randomUUID().toString() + "-" + file.getOriginalFilename();
        
        try (InputStream inputStream = file.getInputStream()) {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(defaultBucketName)
                    .key(fileKey)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, file.getSize()));
            log.info("Successfully uploaded file to S3: s3://{}/{}", defaultBucketName, fileKey);
            return fileKey;
        } catch (Exception e) {
            log.error("Failed to upload file to S3", e);
            throw new IOException("Failed to upload file to S3", e);
        }
    }

    public byte[] downloadFile(String fileKey) throws IOException {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(defaultBucketName)
                    .key(fileKey)
                    .build();

            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(getObjectRequest);
            return objectBytes.asByteArray();
        } catch (Exception e) {
            log.error("Failed to download file from S3", e);
            throw new IOException("Failed to download file from S3", e);
        }
    }

    public void deleteFile(String fileKey) throws IOException {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(defaultBucketName)
                    .key(fileKey)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("Successfully deleted file from S3: {}", fileKey);
        } catch (Exception e) {
            log.error("Failed to delete file from S3", e);
            throw new IOException("Failed to delete file from S3", e);
        }
    }
}
