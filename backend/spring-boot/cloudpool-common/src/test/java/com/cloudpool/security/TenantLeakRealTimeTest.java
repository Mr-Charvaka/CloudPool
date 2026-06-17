package com.cloudpool.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class TenantLeakRealTimeTest {

    private String acquireToken() {
        RestTemplate restTemplate = new RestTemplate();
        String baseUrl = "http://localhost:8080/api";

        // Register user (in case they do not exist)
        Map<String, String> regData = new HashMap<>();
        regData.put("email", "leak_realtime_test@cloudpool.com");
        regData.put("password", "Password123!");
        regData.put("name", "Leak RealTime Test User");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Bypass-Rate-Limit", "cloudpool-test");

        HttpEntity<Map<String, String>> regRequest = new HttpEntity<>(regData, headers);
        try {
            restTemplate.postForEntity(baseUrl + "/auth/register", regRequest, Map.class);
        } catch (Exception ignored) {
            // Already registered
        }

        // Login
        Map<String, String> loginData = new HashMap<>();
        loginData.put("email", "leak_realtime_test@cloudpool.com");
        loginData.put("password", "Password123!");

        HttpEntity<Map<String, String>> loginRequest = new HttpEntity<>(loginData, headers);
        ResponseEntity<Map> loginResponse = restTemplate.postForEntity(baseUrl + "/auth/login", loginRequest, Map.class);
        
        return (String) loginResponse.getBody().get("token");
    }

    @Test
    public void testContextDoesNotLeakAcrossRequests() throws Exception {
        String token = acquireToken();
        assertThat(token).isNotNull();

        RestTemplate restTemplate = new RestTemplate();
        String filesUrl = "http://localhost:8080/api/files";

        // Request 1: Set tenant-a in Header
        HttpHeaders headers1 = new HttpHeaders();
        headers1.set("Authorization", "Bearer " + token);
        headers1.set("X-Tenant-ID", "tenant-a");
        headers1.set("X-Bypass-Rate-Limit", "cloudpool-test");
        HttpEntity<Void> request1 = new HttpEntity<>(headers1);

        ResponseEntity<String> response1 = restTemplate.exchange(filesUrl, HttpMethod.GET, request1, String.class);
        assertThat(response1.getStatusCode().is2xxSuccessful()).isTrue();

        // Request 2: No X-Tenant-ID header. Should NOT see tenant-a context or files
        HttpHeaders headers2 = new HttpHeaders();
        headers2.set("Authorization", "Bearer " + token);
        headers2.set("X-Bypass-Rate-Limit", "cloudpool-test");
        HttpEntity<Void> request2 = new HttpEntity<>(headers2);

        ResponseEntity<String> response2 = restTemplate.exchange(filesUrl, HttpMethod.GET, request2, String.class);
        assertThat(response2.getStatusCode().is2xxSuccessful()).isTrue();

        // Assert Response 2 does not contain any leakage of "tenant-a"
        assertThat(response2.getBody()).doesNotContain("tenant-a");
    }
}
