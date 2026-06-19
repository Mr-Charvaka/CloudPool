package com.cloudpool.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// IMPORTANT: belongs in cloudpool-auth, where ProjectRestController actually lives
@SpringBootTest
@AutoConfigureMockMvc
public class TenantIsolationTest {

    @Autowired 
    private MockMvc mockMvc;

    @Test
    public void userCannotSeeAnotherUsersProjectsViaSpoofedTenantHeader() throws Exception {
        String tokenA = registerAndLogin("tenant-a@test.com");
        String tokenB = registerAndLogin("tenant-b@test.com");

        // Tenant B creates a project that should never be visible to A
        mockMvc.perform(post("/api/v1/projects")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"tenant-b-secret-project\"}"))
            .andExpect(status().isOk());

        // Tenant A tries to see it by spoofing the header — must not appear
        mockMvc.perform(get("/api/v1/projects")
                .header("Authorization", "Bearer " + tokenA)
                .header("X-Tenant-ID", extractUserId(tokenB))) // attempted spoof
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("tenant-b-secret-project"))));
    }

    private String registerAndLogin(String email) throws Exception { 
        // Mocked or actual token fetching implementation for test harness
        // Returning a placeholder here so the skeleton compiles.
        return "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." + java.util.Base64.getEncoder().encodeToString(("{\"sub\":\"" + email + "\"}").getBytes()) + ".signature";
    }

    private String extractUserId(String jwt) { 
        // Mocked user extraction 
        return java.util.UUID.randomUUID().toString();
    }
}
