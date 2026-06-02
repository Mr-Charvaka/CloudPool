package com.cloudpool.repository;

import com.cloudpool.model.ReceivedEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ReceivedEmailRepository extends JpaRepository<ReceivedEmail, UUID> {
}
