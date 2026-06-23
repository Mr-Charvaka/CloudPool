package com.cloudpool;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
    "JWT_SECRET=test-jwt-secret-key-for-testing-purposes-only",
    "CLOUDPOOL_ENCRYPTION_MASTER_KEY=test-master-key-32bytes-test-master-key!!",
    "CLOUDPOOL_ENCRYPTION_SALT=test-salt-value-for-encryption",
})
class CloudpoolNetworkApplicationTest {

    @Test
    void contextLoads() {
    }
}
