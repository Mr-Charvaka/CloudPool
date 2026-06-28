package com.cloudpool.controller;

import com.cloudpool.security.JwtUtils;
import com.cloudpool.security.RefreshRateLimiter;
import com.cloudpool.service.AuditLogService;
import com.cloudpool.service.CacheService;
import com.cloudpool.service.MetricsService;
import com.cloudpool.service.RefreshTokenService;
import com.cloudpool.repository.UserRepository;
import com.cloudpool.repository.BucketRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerRateLimitTest {

    @Mock private UserRepository userRepository;
    @Mock private BucketRepository bucketRepository;
    @Mock private JwtUtils jwtUtils;
    @Mock private CacheService cacheService;
    @Mock private AuditLogService auditLogService;
    @Mock private MetricsService metricsService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private RefreshRateLimiter refreshRateLimiter;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
        ReflectionTestUtils.setField(authController, "jwtExpirationMs", 3600000L);
        ReflectionTestUtils.setField(authController, "refreshTokenExpirationDays", 7);
    }

    @Test
    @DisplayName("Should return 429 when refresh rate limit is exceeded")
    void testRefreshTokenRateLimited() throws Exception {
        Map<String, String> request = Map.of("refreshToken", "cp_refresh_abcdef1234567890");
        doThrow(new com.cloudpool.exception.CloudPoolException("Too many refresh attempts. Try again later."))
                .when(refreshRateLimiter).checkRateLimit(anyString(), anyString());

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(req -> {
                    req.setRemoteAddr("192.168.1.100");
                    return req;
                }))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Too many refresh attempts. Try again later."));

        verify(refreshRateLimiter).checkRateLimit("192.168.1.100", "cp_refresh_abcde");
    }

    @Test
    @DisplayName("Should pass through to refreshTokenService when rate limit is not exceeded")
    void testRefreshTokenWithinLimit() throws Exception {
        Map<String, String> request = Map.of("refreshToken", "cp_refresh_validtoken12345");
        doNothing().when(refreshRateLimiter).checkRateLimit(anyString(), anyString());
        when(refreshTokenService.rotateRefreshToken("cp_refresh_validtoken12345"))
                .thenReturn(new RefreshTokenService.TokenResult("new-access-token", "new-refresh-token", UUID.randomUUID()));

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(req -> {
                    req.setRemoteAddr("10.0.0.50");
                    return req;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));
    }

    @Test
    @DisplayName("Should check rate limit using IP and refresh token prefix")
    void testRefreshTokenUsesIpAndFamilyHint() throws Exception {
        Map<String, String> request = Map.of("refreshToken", "cp_refresh_familyprefix123");
        doNothing().when(refreshRateLimiter).checkRateLimit(anyString(), anyString());
        when(refreshTokenService.rotateRefreshToken(anyString()))
                .thenReturn(new RefreshTokenService.TokenResult("token", "refresh", UUID.randomUUID()));

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(req -> {
                    req.setRemoteAddr("172.16.0.1");
                    return req;
                }))
                .andExpect(status().isOk());

        verify(refreshRateLimiter).checkRateLimit("172.16.0.1", "cp_refresh_famil");
    }

    @Test
    @DisplayName("Should return 400 when refreshToken is missing")
    void testRefreshTokenMissing() throws Exception {
        Map<String, String> request = Map.of();

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("refreshToken is required"));

        verifyNoInteractions(refreshRateLimiter);
    }

    @Test
    @DisplayName("Should return 429 for refresh-cookie endpoint when rate limited")
    void testRefreshFromCookieRateLimited() throws Exception {
        doThrow(new com.cloudpool.exception.CloudPoolException("Too many refresh attempts. Try again later."))
                .when(refreshRateLimiter).checkRateLimit(anyString(), anyString());

        mockMvc.perform(post("/api/auth/refresh-cookie")
                .cookie(new Cookie("cp_refresh", "cp_refresh_cookietoken123"))
                .with(req -> {
                    req.setRemoteAddr("10.10.0.99");
                    return req;
                }))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Too many refresh attempts. Try again later."));
    }

    @Test
    @DisplayName("Should return 401 when no refresh cookie is present")
    void testRefreshFromCookieMissing() throws Exception {
        mockMvc.perform(post("/api/auth/refresh-cookie"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("No refresh token cookie"));
    }
}