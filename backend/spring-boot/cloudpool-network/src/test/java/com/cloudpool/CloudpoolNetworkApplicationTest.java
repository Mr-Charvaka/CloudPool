package com.cloudpool;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
    "JWT_SECRET=this-is-a-very-long-test-jwt-secret-key-that-is-at-least-64-characters-long-for-testing",
    "CLOUDPOOL_ENCRYPTION_MASTER_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "CLOUDPOOL_ENCRYPTION_SALT=dGVzdC1vbmx5LXNhbHQtdmFsdWUtMzItYnl0ZXMtbG9uZyEh",
})
class CloudpoolNetworkApplicationTest {

    @Test
    void contextLoads() {
    }
}
