package com.cloudpool.provider;

import com.cloudpool.model.Bucket;
import com.cloudpool.model.FileMetadata;
import com.cloudpool.spi.StorageProvider;
import com.cloudpool.exception.CloudPoolException;
import org.springframework.stereotype.Component;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ByteArrayResource;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;

@Slf4j
@Component
public class GoogleDriveProvider implements StorageProvider {

    @Override
    public void uploadFile(Bucket bucket, FileMetadata metadata, InputStream data) {
        log.info("GoogleDriveProvider: Uploading file {} to bucket {}", metadata.getOriginalName(), bucket.getName());
        try {
            Thread.sleep(500);
            metadata.setProviderFileId("gdrive-" + java.util.UUID.randomUUID().toString());
            log.info("GoogleDriveProvider: Upload complete. FileId: {}", metadata.getProviderFileId());
        } catch (Exception e) {
            throw new CloudPoolException("Failed to upload to Google Drive", e);
        }
    }

    @Override
    public Resource downloadFile(FileMetadata metadata) {
        log.info("GoogleDriveProvider: Downloading file {}", metadata.getProviderFileId());
        return new ByteArrayResource("Simulated File Content from Google Drive".getBytes());
    }

    @Override
    public void deleteFile(FileMetadata metadata) {
        log.info("GoogleDriveProvider: Deleting file {}", metadata.getProviderFileId());
    }

    @Override
    public String getProviderName() {
        return "GOOGLE_DRIVE";
    }
}
