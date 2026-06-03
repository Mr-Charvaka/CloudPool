package com.cloudpool.filter;

import com.cloudpool.service.AnalyticsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AnalyticsFilter extends OncePerRequestFilter {

    private final AnalyticsService analyticsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        
        // Only log API and GraphQL traffic, ignoring static assets and console
        boolean shouldLog = (path.startsWith("/api/") || path.startsWith("/graphql")) 
                && !path.startsWith("/api/dev/emails") 
                && !path.startsWith("/api/analytics");

        long startTime = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (shouldLog) {
                long duration = System.currentTimeMillis() - startTime;
                
                String projectIdHeader = request.getHeader("X-Project-Id");
                UUID projectId = null;
                if (projectIdHeader != null && !projectIdHeader.trim().isEmpty()) {
                    try {
                        projectId = UUID.fromString(projectIdHeader.trim());
                    } catch (Exception ignored) {}
                }
                
                try {
                    analyticsService.logRequest(
                            projectId,
                            path,
                            request.getMethod(),
                            response.getStatus(),
                            duration,
                            request.getRemoteAddr(),
                            request.getHeader("User-Agent")
                    );
                } catch (Exception e) {
                    logger.error("Failed to record request analytics: " + e.getMessage());
                }
            }
        }
    }
}
