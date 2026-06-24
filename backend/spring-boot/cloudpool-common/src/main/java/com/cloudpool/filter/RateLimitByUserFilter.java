package com.cloudpool.filter;

import com.cloudpool.config.RateLimitConfig;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(2)
public class RateLimitByUserFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitByUserFilter.class);
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final RateLimitConfig config;

    public RateLimitByUserFilter(RateLimitConfig config) {
        this.config = config;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String userId = resolveUserId(req);
        Bucket bucket = buckets.computeIfAbsent(userId, k -> createBucket());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            res.setStatus(429);
            res.setContentType("application/problem+json");
            res.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Rate limit exceeded. Try again later.\"}");
        }
    }

    private String resolveUserId(HttpServletRequest req) {
        String apiKey = req.getHeader("X-API-KEY");
        if (apiKey != null) return apiKey;
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) return auth.substring(7);
        return req.getRemoteAddr();
    }

    private Bucket createBucket() {
        Bandwidth limit = Bandwidth.classic(config.getRequestsPerMinute(), Refill.greedy(config.getRequestsPerMinute(), Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }
}