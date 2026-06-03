package com.cloudpool.service;

import com.cloudpool.model.User;
import com.cloudpool.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaService {

    private final UserRepository userRepository;

    /**
     * Atomically check and reserve user quota in an isolated transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reserveQuota(UUID userId, long size) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        long newUsage = user.getCurrentUsage() + size;
        if (newUsage > user.getStorageQuota()) {
            log.warn("Quota check failed for user {}: current={}, requested={}, limit={}",
                    userId, user.getCurrentUsage(), size, user.getStorageQuota());
            return false;
        }
        
        user.setCurrentUsage(newUsage);
        userRepository.save(user);
        log.info("Quota reservation successful for user {}: allocated {} bytes. Current total: {}", userId, size, newUsage);
        return true;
    }

    /**
     * Atomically release user quota in an isolated transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseQuota(UUID userId, long size) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        long newUsage = Math.max(0, user.getCurrentUsage() - size);
        user.setCurrentUsage(newUsage);
        userRepository.save(user);
        log.info("Quota release successful for user {}: freed {} bytes. Current total: {}", userId, size, newUsage);
    }
}
