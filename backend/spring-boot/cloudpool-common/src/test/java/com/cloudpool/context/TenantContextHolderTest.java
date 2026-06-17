package com.cloudpool.context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class TenantContextHolderTest {

    @BeforeEach
    public void setUp() {
        TenantContextHolder.clear();
    }

    @Test
    public void testSetAndGetTenantId() {
        String tenantId = "tenant-12345";
        TenantContextHolder.setTenantId(tenantId);
        assertEquals(tenantId, TenantContextHolder.getTenantId());
    }

    @Test
    public void testSetAndGetUserId() {
        String userId = "user-67890";
        TenantContextHolder.setUserId(userId);
        assertEquals(userId, TenantContextHolder.getUserId());
    }

    @Test
    public void testClearContext() {
        TenantContextHolder.setTenantId("tenant-abc");
        TenantContextHolder.setUserId("user-xyz");
        
        TenantContextHolder.clear();
        
        assertNull(TenantContextHolder.getTenantId());
        assertNull(TenantContextHolder.getUserId());
    }

    @Test
    public void testThreadLocalIsolation() throws InterruptedException {
        TenantContextHolder.setTenantId("main-thread-tenant");
        TenantContextHolder.setUserId("main-thread-user");

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> subThreadTenantId = new AtomicReference<>();
        final AtomicReference<String> subThreadUserId = new AtomicReference<>();

        Thread thread = new Thread(() -> {
            // Check that it's initially null in the new thread
            subThreadTenantId.set(TenantContextHolder.getTenantId());
            subThreadUserId.set(TenantContextHolder.getUserId());

            // Set new values for this sub-thread
            TenantContextHolder.setTenantId("sub-thread-tenant");
            TenantContextHolder.setUserId("sub-thread-user");

            latch.countDown();
        });

        thread.start();
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        // Sub-thread initially saw nulls because ThreadLocal isolates it
        assertNull(subThreadTenantId.get());
        assertNull(subThreadUserId.get());

        // Main thread values remain untouched
        assertEquals("main-thread-tenant", TenantContextHolder.getTenantId());
        assertEquals("main-thread-user", TenantContextHolder.getUserId());
    }
}
