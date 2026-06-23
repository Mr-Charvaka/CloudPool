package com.cloudpool.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

@Slf4j
@Component
public class LoginRateLimiterFilter implements Filter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MS = 60000;

    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String LUA_SCRIPT =
            "local count = redis.call('INCR', KEYS[1]) " +
            "if count == 1 then " +
            "  redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
            "end " +
            "return count";
    private final RedisScript<Long> script = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);

    private final Map<String, IpWindow> localAttempts = new ConcurrentHashMap<>();
    private final Object localLock = new Object();

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
                Long count = redisTemplate.execute(script, Collections.singletonList(key), 60);
                return count != null && count > MAX_ATTEMPTS;
            } catch (Exception e) {
                log.debug("Redis rate limiting error, falling back to local storage: {}", e.getMessage());
            }
        }

        long now = System.currentTimeMillis();
        synchronized (localLock) {
            IpWindow window = localAttempts.get(ip);
            if (window == null || now - window.windowStart > WINDOW_MS) {
                window = new IpWindow(now, 0);
                localAttempts.put(ip, window);
            }
            window.count++;
            if (window.count > MAX_ATTEMPTS) {
                return true;
            }
        }
        return false;
    }

    private static class IpWindow {
        final long windowStart;
        int count;

        IpWindow(long windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
