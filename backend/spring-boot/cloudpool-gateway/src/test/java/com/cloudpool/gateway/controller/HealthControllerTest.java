package com.cloudpool.gateway.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;

@WebFluxTest(controllers = HealthController.class, excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration.class})
@Import(HealthControllerTestConfig.class)
class HealthControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void getHealth_shouldReturnUp() {
        webTestClient.get().uri("/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.uptime").isNotEmpty()
                .jsonPath("$.uptimeMs").isNumber();
    }

    @Test
    void getHealth_withDetails_shouldIncludeSystem() {
        webTestClient.get().uri("/health?details=true")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.system").exists()
                .jsonPath("$.system.availableProcessors").isNumber()
                .jsonPath("$.system.jvmTotalMemory").isNotEmpty()
                .jsonPath("$.system.jvmFreeMemory").isNotEmpty()
                .jsonPath("$.system.jvmMaxMemory").isNotEmpty()
                .jsonPath("$.system.jvmUsedMemory").isNotEmpty();
    }

    @Test
    void getHealth_withAdvanced_shouldIncludeServicePings() {
        webTestClient.get().uri("/health?advanced=true")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.services").exists()
                .jsonPath("$.services.auth").exists()
                .jsonPath("$.services.data").exists()
                .jsonPath("$.services.compute").exists()
                .jsonPath("$.services.network").exists();
    }

    @Test
    void getHealth_withDetailsTrue_shouldIncludeBothSystemAndServices() {
        webTestClient.get().uri("/health?details=true")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.system").exists()
                .jsonPath("$.services").exists();
    }

    @Test
    void getApiHealth_shouldAlsoWork() {
        webTestClient.get().uri("/api/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }
}
