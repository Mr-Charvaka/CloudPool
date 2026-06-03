package com.cloudpool.repository;

import com.cloudpool.model.DevTableField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface DevTableFieldRepository extends JpaRepository<DevTableField, UUID> {
    List<DevTableField> findByTableId(UUID tableId);
    void deleteByTableId(UUID tableId);
}
