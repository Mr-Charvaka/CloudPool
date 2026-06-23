package com.cloudpool.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

@SpringBootTest
@TestPropertySource(properties = {
    "cors.allowed-origins=http://localhost:3000",
    "cloudpool.services.auth.url=http://localhost:8082",
    "cloudpool.services.data.url=http://localhost:8083",
    "cloudpool.services.compute.url=http://localhost:8084",
    "cloudpool.services.network.url=http://localhost:8085",
})
class GatewayApplicationTest {

    @MockBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    @Test
    void contextLoads() {
    }
}
