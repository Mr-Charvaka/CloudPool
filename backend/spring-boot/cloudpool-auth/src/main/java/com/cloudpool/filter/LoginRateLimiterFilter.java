package com.cloudpool.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class LoginRateLimiterFilter implements Filter {

    private final RedisTemplate<String, Object> redisTemplate;
    private final Map<String, Long> localAttempts = new ConcurrentHashMap<>();

    public LoginRateLimiterFilter(Optional<RedisTemplate<String, Object>> redisTemplate) {
        this.redisTemplate = redisTemplate.orElse(null);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        if ("POST".equalsIgnoreCase(req.getMethod()) && "/api/auth/login".equalsIgnoreCase(req.getRequestURI())) {
            String ip = req.getRemoteAddr();
            if (isRateLimited(ip)) {
                log.warn("Login attempt rate limited for IP: {}", ip);
                res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                res.setContentType("application/json");
                res.getWriter().write("{\"error\": \"Too many login attempts. Please try again later.\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private boolean isRateLimited(String ip) {
        String key = "rate:login:" + ip;
        if (redisTemplate != null) {
            try {
                Long count = redisTemplate.opsForValue().increment(key);
                if (count == null || count == 1) {
                    redisTemplate.expire(key, 60, TimeUnit.SECONDS);
                }
                return count != null && count > 5; // Allow 5 attempts per minute
            } catch (Exception e) {
                log.debug("Redis rate limiting error, falling back to local storage: {}", e.getMessage());
            }
        }

        // Local in-memory fallback
        long now = System.currentTimeMillis();
        localAttempts.entrySet().removeIf(entry -> now - entry.getValue() > 60000);
        long count = localAttempts.keySet().stream().filter(k -> k.startsWith(ip)).count();
        if (count >= 5) {
            return true;
        }
        localAttempts.put(ip + "_" + now, now);
        return false;
    }
}
