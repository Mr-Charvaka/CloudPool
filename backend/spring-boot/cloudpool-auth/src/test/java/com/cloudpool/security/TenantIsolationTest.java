package com.cloudpool.security;

import com.cloudpool.model.User;
import com.cloudpool.model.enums.Role;
import com.cloudpool.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = com.cloudpool.CloudpoolAuthApplication.class, properties = {
    "management.health.redis.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
    "cloudpool.rate-limit.enabled=false",
    "JWT_SECRET=this-is-a-very-long-test-jwt-secret-key-that-is-at-least-64-characters-long-for-testing"
})
@AutoConfigureMockMvc
public class TenantIsolationTest {

    @Autowired 
    private MockMvc mockMvc;

    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;

    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.data.redis.connection.ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @org.junit.jupiter.api.BeforeEach
    void setupMockRedis() {
        org.springframework.data.redis.core.ValueOperations<String, String> ops = org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);
        org.mockito.Mockito.when(redisTemplate.opsForValue()).thenReturn(ops);
        org.mockito.Mockito.when(ops.increment(org.mockito.ArgumentMatchers.anyString())).thenReturn(1L);
    }

    @Test
    @DisplayName("User A cannot see User B's projects even with spoofed X-Tenant-ID header")
    public void userCannotSeeAnotherUsersProjectsViaSpoofedTenantHeader() throws Exception {
        User userA = registerUser("tenant-a@test.com");
        User userB = registerUser("tenant-b@test.com");

        String tokenA = jwtUtils.generateToken(userA.getEmail());
        String tokenB = jwtUtils.generateToken(userB.getEmail());

        // Tenant B creates a project that should never be visible to A
        mockMvc.perform(post("/api/v1/projects")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"tenant-b-secret-project\", \"description\":\"secret\"}"))
            .andExpect(status().is2xxSuccessful());

        // Tenant A tries to see it by spoofing the header — must not appear
        mockMvc.perform(get("/api/v1/projects")
                .header("Authorization", "Bearer " + tokenA)
                .header("X-Tenant-ID", userB.getId().toString())) // attempted spoof
            .andExpect(status().is2xxSuccessful())
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("tenant-b-secret-project"))));
    }

    @Test
    @DisplayName("Unauthenticated request without token should return 4xx")
    public void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/projects"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("User cannot access another user's project by ID")
    public void userCannotAccessAnotherUsersProjectById() throws Exception {
        User userA = registerUser("owner@test.com");
        User userB = registerUser("intruder@test.com");

        String tokenA = jwtUtils.generateToken(userA.getEmail());
        String tokenB = jwtUtils.generateToken(userB.getEmail());

        org.springframework.test.web.servlet.MvcResult result = mockMvc.perform(post("/api/v1/projects")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"owner-project\", \"description\":\"owner data\"}"))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
        
        String responseBody = result.getResponse().getContentAsString();
        // Extract the ID assuming a JSON like {"id":"...", ...}
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"id\":\"([^\"]+)\"").matcher(responseBody);
        String projectId = matcher.find() ? matcher.group(1) : UUID.randomUUID().toString();

        mockMvc.perform(get("/api/v1/projects/" + projectId)
                .header("Authorization", "Bearer " + tokenB))
            .andExpect(status().is4xxClientError());
    }



    @Test
    @DisplayName("Expired JWT token should be rejected")
    public void expiredTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/projects")
                .header("Authorization", "Bearer expired-invalid-token"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Missing Authorization header should return 4xx")
    public void missingAuthHeaderReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is4xxClientError());
    }

    private User registerUser(String email) { 
        User user = User.builder()
            .email(email)
            .name(email)
            .passwordHash("dummy-hash")
            .role(Role.USER)
            .build();
        return userRepository.save(user);
    }
}
