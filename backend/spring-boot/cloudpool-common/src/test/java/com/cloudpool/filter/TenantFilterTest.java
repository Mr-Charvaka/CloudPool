package com.cloudpool.filter;

import com.cloudpool.context.TenantContextHolder;
import com.cloudpool.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TenantFilterTest {

    private TenantFilter tenantFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        tenantFilter = new TenantFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should extract tenant identity securely from SecurityContext and not HTTP headers")
    void shouldExtractTenantFromSecurityContext() throws ServletException, IOException {
        // Arrange: authenticated user
        UUID userId = UUID.randomUUID();
        User mockUser = User.builder().id(userId).email("test@example.com").build();
        
        UsernamePasswordAuthenticationToken auth = 
            new UsernamePasswordAuthenticationToken(mockUser, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Act: use a capturing filter chain to verify tenant ID IS SET during the request
        final boolean[] contextWasSet = {false};
        MockFilterChain capturingChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                // Verify tenant IS SET during request processing
                assertNotNull(TenantContextHolder.getTenantId(), "Tenant ID must be set during request");
                assertEquals(userId.toString(), TenantContextHolder.getTenantId(), "Tenant ID must match user ID");
                assertEquals(userId.toString(), TenantContextHolder.getUserId(), "User ID must be set during request");
                contextWasSet[0] = true;
            }
        };
        tenantFilter.doFilterInternal(request, response, capturingChain);

        // Assert: context IS cleared after filter chain in the finally block
        assertNull(TenantContextHolder.getTenantId(), "Context must be cleared after filter chain to prevent memory leaks and cross-request poisoning");
        assertNull(TenantContextHolder.getUserId(), "Context must be cleared after filter chain to prevent memory leaks and cross-request poisoning");
        assertTrue(contextWasSet[0], "Capturing chain must have been invoked");
    }

    @Test
    @DisplayName("Should skp tenant context if user is unauthenticated")
    void shouldSkipIfUnauthenticated() throws ServletException, IOException {
        // Act with empty security context
        final boolean[] chainInvoked = {false};
        MockFilterChain capturingChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                // Verify tenant context is NOT set for unauthenticated users
                assertNull(TenantContextHolder.getTenantId(), "Tenant ID must not be set for unauthenticated requests");
                chainInvoked[0] = true;
            }
        };
        tenantFilter.doFilterInternal(request, response, capturingChain);

        // Assert
        assertNull(TenantContextHolder.getTenantId());
        assertTrue(chainInvoked[0], "Filter chain must have been invoked");
    }
}
