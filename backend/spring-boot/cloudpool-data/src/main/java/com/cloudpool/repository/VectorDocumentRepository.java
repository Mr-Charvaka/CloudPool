package com.cloudpool.repository;

import com.cloudpool.model.VectorDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VectorDocumentRepository extends JpaRepository<VectorDocument, UUID> {
    List<VectorDocument> findByCollectionId(UUID collectionId);
    Optional<VectorDocument> findByCollectionIdAndDocId(UUID collectionId, String docId);
    void deleteByCollectionId(UUID collectionId);
    void deleteByCollectionIdAndDocId(UUID collectionId, String docId);
}
