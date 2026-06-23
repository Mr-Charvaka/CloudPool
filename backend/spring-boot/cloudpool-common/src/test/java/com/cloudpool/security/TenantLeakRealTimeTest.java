package com.cloudpool.security;

import com.cloudpool.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TenantLeakRealTimeTest {

    @BeforeEach
    @AfterEach
    void cleanContext() {
        TenantContextHolder.clear();
    }

    @Test
    public void testContextDoesNotLeakAcrossRequests() {
        TenantContextHolder.setTenantId("tenant-a");
        assertThat(TenantContextHolder.getTenantId()).isEqualTo("tenant-a");

        TenantContextHolder.clear();
        assertThat(TenantContextHolder.getTenantId()).isNull();

        TenantContextHolder.setTenantId("tenant-b");
        assertThat(TenantContextHolder.getTenantId()).isEqualTo("tenant-b");

        TenantContextHolder.clear();
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    public void testDifferentTenantContextsAreIsolated() {
        TenantContextHolder.setTenantId("tenant-a");
        TenantContextHolder.setUserId("user-a");

        assertThat(TenantContextHolder.getTenantId()).isEqualTo("tenant-a");
        assertThat(TenantContextHolder.getUserId()).isEqualTo("user-a");

        TenantContextHolder.clear();

        TenantContextHolder.setTenantId("tenant-b");
        TenantContextHolder.setUserId("user-b");

        assertThat(TenantContextHolder.getTenantId()).isEqualTo("tenant-b");
        assertThat(TenantContextHolder.getUserId()).isEqualTo("user-b");

        assertThat(TenantContextHolder.getTenantId()).doesNotContain("tenant-a");
        assertThat(TenantContextHolder.getUserId()).doesNotContain("user-a");
    }
}
