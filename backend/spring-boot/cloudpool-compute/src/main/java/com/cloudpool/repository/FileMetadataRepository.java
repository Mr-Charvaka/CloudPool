package com.cloudpool.repository;

import com.cloudpool.model.Bucket;
import com.cloudpool.model.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID> {
    List<FileMetadata> findByBucket(Bucket bucket);
    
    @Query("SELECT f FROM FileMetadata f WHERE f.bucket.user.id = :userId")
    List<FileMetadata> findByUserId(@Param("userId") UUID userId);

    @Query("SELECT f FROM FileMetadata f WHERE f.bucket.user.id = :userId AND LOWER(f.originalName) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<FileMetadata> searchFiles(@Param("userId") UUID userId, @Param("query") String query);
}
