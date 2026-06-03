package com.cloudpool.filter;

import com.cloudpool.context.TenantContextHolder;
import com.cloudpool.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            // Extract tenant and user from authentication
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof User) {
                User user = (User) authentication.getPrincipal();

                // Set context
                TenantContextHolder.setUserId(user.getId().toString());
                
                // Get tenant from header or default to user's own id
                String tenantId = request.getHeader("X-Tenant-ID");
                if (tenantId != null && !tenantId.trim().isEmpty()) {
                    TenantContextHolder.setTenantId(tenantId);
                } else {
                    TenantContextHolder.setTenantId(user.getId().toString());
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
