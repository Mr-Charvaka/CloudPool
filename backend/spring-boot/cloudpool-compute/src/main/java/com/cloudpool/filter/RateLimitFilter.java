package com.cloudpool.filter;
 
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
 
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
 
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {
 
    @Value("${cloudpool.rate-limit.requests-per-minute:120}")
    private double requestsPerMinute;
 
    private final LoadingCache<String, RateLimiter> limiters = CacheBuilder.newBuilder()
        .maximumSize(10000)
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build(new CacheLoader<>() {
            @Override
            public RateLimiter load(String key) {
                return RateLimiter.create(requestsPerMinute / 60.0); // Per second
            }
        });
 
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) 
            throws ServletException, IOException {
        
        // Skip rate limiting for public static files / auth endpoints
        if (isPublicEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }
 
        String clientId = getClientId(request);
        
        try {
            RateLimiter rateLimiter = limiters.get(clientId);
            
            if (!rateLimiter.tryAcquire(1, 1, TimeUnit.SECONDS)) {
                log.warn("Rate limit exceeded for client: {}", clientId);
                response.setStatus(429); // SC_TOO_MANY_REQUESTS
                response.setHeader("Retry-After", "60");
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
                return;
            }
        } catch (ExecutionException e) {
            log.error("Rate limiter error: {}", e.getMessage());
        }
 
        filterChain.doFilter(request, response);
    }
 
    /**
     * Get client identifier (IP, API key, or user)
     */
    private String getClientId(HttpServletRequest request) {
        // Try API key first
        String apiKey = request.getHeader("X-API-KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            return "api-key:" + apiKey;
        }
 
        // Fall back to IP address
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = request.getRemoteAddr();
        }
        return "ip:" + clientIp;
    }
 
    /**
     * Check if endpoint should be rate limited
     */
    private boolean isPublicEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/") || 
               path.startsWith("/error") || 
               path.equals("/") || 
               path.equals("/index.html") || 
               path.startsWith("/static/") || 
               path.equals("/favicon.ico");
    }
}
