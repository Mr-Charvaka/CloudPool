package com.cloudpool.repository;

import com.cloudpool.model.OutboxEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface OutboxEmailRepository extends JpaRepository<OutboxEmail, UUID> {
}
