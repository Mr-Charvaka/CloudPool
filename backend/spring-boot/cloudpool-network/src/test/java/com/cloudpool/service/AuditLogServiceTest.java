package com.cloudpool.service;

import com.cloudpool.model.AuditLog;
import com.cloudpool.model.User;
import com.cloudpool.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock private AuditLogRepository auditLogRepository;

    private AuditLogService auditLogService;
    private User testUser;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(auditLogRepository);
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@test.com")
                .build();
    }

    @Test
    void log_withFullContext_shouldSaveEntry() {
        auditLogService.log(testUser, "USER_LOGIN", "AUTH", null, "Login successful",
                "192.168.1.1", "Mozilla/5.0");

        verify(auditLogRepository, timeout(1000)).save(any(AuditLog.class));
    }

    @Test
    void log_withoutIpAndAgent_shouldSaveEntry() {
        auditLogService.log(testUser, "FILE_UPLOAD", "FILE", "file-123", "Uploaded report.pdf");

        verify(auditLogRepository, timeout(1000)).save(any(AuditLog.class));
    }

    @Test
    void log_withMinimalContext_shouldSaveEntry() {
        auditLogService.log(testUser, "USER_LOGOUT", "10.0.0.1");

        verify(auditLogRepository, timeout(1000)).save(any(AuditLog.class));
    }

    @Test
    void log_withAnonymousUser_shouldHandleNull() {
        auditLogService.log(null, "USER_LOGIN_FAILED", "AUTH", null, "Failed login", "10.0.0.1", null);

        verify(auditLogRepository, timeout(1000)).save(any(AuditLog.class));
    }

    @Test
    void log_whenRepositoryThrows_shouldNotPropagate() {
        doThrow(new RuntimeException("DB error")).when(auditLogRepository).save(any(AuditLog.class));

        auditLogService.log(testUser, "USER_LOGIN", "AUTH", null, null, null, null);
        // Should not throw — audit failures must not bubble up
    }

    @Test
    void getRecentLogs_shouldReturnList() {
        UUID userId = testUser.getId();
        when(auditLogRepository.findLatestLogs(eq(userId), any()))
                .thenReturn(List.of());

        List<AuditLog> logs = auditLogService.getRecentLogs(userId, 10);
        assertNotNull(logs);
        assertTrue(logs.isEmpty());
    }

    @Test
    void actionConstants_shouldBeDefined() {
        assertEquals("USER_LOGIN", AuditLogService.ACTION_LOGIN);
        assertEquals("USER_LOGIN_FAILED", AuditLogService.ACTION_LOGIN_FAILED);
        assertEquals("USER_LOGOUT", AuditLogService.ACTION_LOGOUT);
        assertEquals("USER_REGISTER", AuditLogService.ACTION_REGISTER);
        assertEquals("FILE_UPLOAD", AuditLogService.ACTION_FILE_UPLOAD);
        assertEquals("FILE_DOWNLOAD", AuditLogService.ACTION_FILE_DOWNLOAD);
        assertEquals("FILE_DELETE", AuditLogService.ACTION_FILE_DELETE);
        assertEquals("BUCKET_CREATE", AuditLogService.ACTION_BUCKET_CREATE);
        assertEquals("BUCKET_DELETE", AuditLogService.ACTION_BUCKET_DELETE);
        assertEquals("API_KEY_CREATE", AuditLogService.ACTION_API_KEY_CREATE);
        assertEquals("API_KEY_DELETE", AuditLogService.ACTION_API_KEY_DELETE);
        assertEquals("TABLE_CREATE", AuditLogService.ACTION_TABLE_CREATE);
        assertEquals("TABLE_DROP", AuditLogService.ACTION_TABLE_DROP);
        assertEquals("VECTOR_INDEX", AuditLogService.ACTION_VECTOR_INDEX);
    }
}
