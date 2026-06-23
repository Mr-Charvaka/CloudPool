package com.cloudpool.repository;

import com.cloudpool.model.PaymentTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    Page<PaymentTransaction> findByGatewayIdOrderByCreatedAtDesc(UUID gatewayId, Pageable pageable);

    long countByGatewayIdAndStatus(UUID gatewayId, PaymentTransaction.Status status);

    long countByGatewayId(UUID gatewayId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM PaymentTransaction t WHERE t.gateway.id = :gatewayId AND t.status = 'SUCCESS'")
    BigDecimal sumSuccessfulAmountByGatewayId(@Param("gatewayId") UUID gatewayId);

    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);
}
