package com.cloudpool.controller;

import com.cloudpool.model.User;
import com.cloudpool.model.Bucket;
import com.cloudpool.model.enums.Role;
import com.cloudpool.repository.UserRepository;
import com.cloudpool.repository.BucketRepository;
import com.cloudpool.security.JwtUtils;
import com.cloudpool.service.AuditLogService;
import com.cloudpool.service.CacheService;
import com.cloudpool.service.MetricsService;
import com.cloudpool.service.RefreshTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String SECURE_SECRET = "d1f88c8078c1db294e82b71be5e8f6e80b2a75ffca79b9e6e6a1a8c3d6e5a6b0c2e3f4g5h6j7k8l9m0n1p2q3r4s5t6u7v8w9x0y1z2a3b4c5d6e7f8g9";

    @Mock private UserRepository userRepository;
    @Mock private BucketRepository bucketRepository;
    @Mock private JwtUtils jwtUtils;
    @Mock private CacheService cacheService;
    @Mock private AuditLogService auditLogService;
    @Mock private MetricsService metricsService;
    @Mock private RefreshTokenService refreshTokenService;

    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
        ReflectionTestUtils.setField(authController, "jwtExpirationMs", 3600000L);
    }

    @Test
    void testRegisterSuccess() throws Exception {
        Map<String, String> request = Map.of(
            "name", "Test User",
            "email", "test@cloudpool.com",
            "password", "password123"
        );

        when(userRepository.existsByEmail("test@cloudpool.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        var tokenPair = new RefreshTokenService.TokenResult("test-jwt-token", "test-refresh-token", UUID.randomUUID());
        when(refreshTokenService.createTokenPair(any(UUID.class), eq("test@cloudpool.com"))).thenReturn(tokenPair);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("test-jwt-token"))
                .andExpect(jsonPath("$.name").value("Test User"))
                .andExpect(jsonPath("$.email").value("test@cloudpool.com"));

        verify(userRepository).existsByEmail("test@cloudpool.com");
        verify(userRepository).save(any(User.class));
        verify(bucketRepository).save(any(Bucket.class));
    }

    @Test
    void testRegisterDuplicateEmail() throws Exception {
        Map<String, String> request = Map.of(
            "name", "Test User",
            "email", "existing@cloudpool.com",
            "password", "password123"
        );

        when(userRepository.existsByEmail("existing@cloudpool.com")).thenReturn(true);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Email is already in use"));
    }

    @Test
    void testLoginSuccess() throws Exception {
        String email = "user@cloudpool.com";
        String password = "password123";
        String hashedPassword = passwordEncoder.encode(password);
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .name("Test User")
                .passwordHash(hashedPassword)
                .role(Role.USER)
                .active(true)
                .build();

        Map<String, String> request = Map.of("email", email, "password", password);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        var tokenPair = new RefreshTokenService.TokenResult("test-jwt-token", "test-refresh-token", UUID.randomUUID());
        when(refreshTokenService.createTokenPair(any(UUID.class), eq(email))).thenReturn(tokenPair);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("test-jwt-token"))
                .andExpect(jsonPath("$.name").value("Test User"))
                .andExpect(jsonPath("$.email").value(email));

        verify(metricsService).incrementAuthSuccess();
    }

    @Test
    void testLoginInvalidCredentials() throws Exception {
        String email = "user@cloudpool.com";

        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        Map<String, String> request = Map.of("email", email, "password", "wrongpassword");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password"));

        verify(metricsService).incrementAuthFailure();
    }

    @Test
    void testLoginSuspendedUser() throws Exception {
        String email = "suspended@cloudpool.com";
        String password = "password123";
        String hashedPassword = passwordEncoder.encode(password);
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .name("Suspended User")
                .passwordHash(hashedPassword)
                .role(Role.USER)
                .active(false)
                .build();

        Map<String, String> request = Map.of("email", email, "password", password);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("User account is suspended"));

        verify(metricsService).incrementAuthFailure();
    }

    @Test
    void testLogoutWithToken() throws Exception {
        String token = "valid-jwt-token";
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@cloudpool.com")
                .name("Test User")
                .build();

        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        user, null, java.util.Collections.singletonList(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));

        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        verify(cacheService).blacklistToken(eq(token), anyLong());
    }

    @Test
    void testLogoutWithoutToken() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }
}
