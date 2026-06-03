package com.cloudpool.repository;

import com.cloudpool.model.WafRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WafRuleRepository extends JpaRepository<WafRule, UUID> {
    List<WafRule> findByProjectIdAndIsActiveTrue(UUID projectId);
}
