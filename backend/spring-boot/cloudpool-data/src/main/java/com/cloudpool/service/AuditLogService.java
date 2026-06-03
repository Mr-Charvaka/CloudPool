package com.cloudpool.service;

import com.cloudpool.model.AuditLog;
import com.cloudpool.model.User;
import com.cloudpool.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * AuditLogService records security and data events to the audit_logs table.
 *
 * Design decisions:
 * - All write methods are @Async to avoid adding latency to the hot path.
 * - Writes use REQUIRES_NEW propagation so an audit entry is always committed
 *   even if the calling transaction rolls back (e.g. a failed login attempt
 *   should still be recorded).
 * - The service is null-safe: if the user is unauthenticated, userId is null.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    // ── Action constants ────────────────────────────────────────────────────

    public static final String ACTION_LOGIN          = "USER_LOGIN";
    public static final String ACTION_LOGIN_FAILED   = "USER_LOGIN_FAILED";
    public static final String ACTION_LOGOUT         = "USER_LOGOUT";
    public static final String ACTION_REGISTER       = "USER_REGISTER";
    public static final String ACTION_FILE_UPLOAD    = "FILE_UPLOAD";
    public static final String ACTION_FILE_DOWNLOAD  = "FILE_DOWNLOAD";
    public static final String ACTION_FILE_DELETE    = "FILE_DELETE";
    public static final String ACTION_BUCKET_CREATE  = "BUCKET_CREATE";
    public static final String ACTION_BUCKET_DELETE  = "BUCKET_DELETE";
    public static final String ACTION_API_KEY_CREATE = "API_KEY_CREATE";
    public static final String ACTION_API_KEY_DELETE = "API_KEY_DELETE";
    public static final String ACTION_TABLE_CREATE   = "TABLE_CREATE";
    public static final String ACTION_TABLE_DROP     = "TABLE_DROP";
    public static final String ACTION_VECTOR_INDEX   = "VECTOR_INDEX";

    // ── Write API ───────────────────────────────────────────────────────────

    /**
     * Record an audit event asynchronously with full context.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(User user,
                    String action,
                    String resourceType,
                    String resourceId,
                    String details,
                    String ipAddress,
                    String userAgent) {
        try {
            AuditLog entry = AuditLog.builder()
                    .user(user)
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .details(details)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .build();
            auditLogRepository.save(entry);
            log.debug("Audit: {} {} {} by user={}", action, resourceType, resourceId,
                    user != null ? user.getId() : "anonymous");
        } catch (Exception e) {
            // Audit failures must never bubble up and break the caller
            log.error("Failed to write audit log entry: action={}, error={}", action, e.getMessage());
        }
    }

    /**
     * Convenience overload — no IP/userAgent (for internal system actions).
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(User user, String action, String resourceType, String resourceId, String details) {
        log(user, action, resourceType, resourceId, details, null, null);
    }

    /**
     * Convenience overload — minimal (action only, for auth events).
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(User user, String action, String ipAddress) {
        log(user, action, "AUTH", null, null, ipAddress, null);
    }

    // ── Read API ────────────────────────────────────────────────────────────

    /**
     * Fetch the most recent N audit log entries for a given user.
     */
    @Transactional(readOnly = true)
    public List<AuditLog> getRecentLogs(UUID userId, int limit) {
        return auditLogRepository.findLatestLogs(userId, PageRequest.of(0, limit));
    }
}
