package com.cloudpool.repository;

import com.cloudpool.model.PaymentGateway;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentGatewayRepository extends JpaRepository<PaymentGateway, UUID> {

    List<PaymentGateway> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<PaymentGateway> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndDisplayName(UUID userId, String displayName);
}
