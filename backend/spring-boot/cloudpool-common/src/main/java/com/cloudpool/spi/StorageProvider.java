package com.cloudpool.spi;

import com.cloudpool.model.Bucket;
import com.cloudpool.model.FileMetadata;
import org.springframework.core.io.Resource;
import java.io.InputStream;

public interface StorageProvider {
    void uploadFile(Bucket bucket, FileMetadata metadata, InputStream data);
    Resource downloadFile(FileMetadata metadata);
    void deleteFile(FileMetadata metadata);
    String getProviderName();
}
