package com.cloudpool.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

@Component
@Order(0)
public class IdempotencyFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;

    public IdempotencyFilter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String method = req.getMethod();
        if (!method.equals("POST") && !method.equals("PATCH") && !method.equals("PUT")) {
            chain.doFilter(request, response);
            return;
        }

        String idempotencyKey = req.getHeader("Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        String cacheKey = "idempotency:" + idempotencyKey;
        Boolean alreadySeen = redis.hasKey(cacheKey);

        if (Boolean.TRUE.equals(alreadySeen)) {
            String previousResponse = redis.opsForValue().get(cacheKey);
            log.debug("Idempotency hit for key={}", idempotencyKey);
            res.setStatus(409);
            res.setContentType("application/problem+json");
            res.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Conflict\",\"status\":409,\"detail\":\"Request with this Idempotency-Key has already been processed\"}");
            return;
        }

        redis.opsForValue().set(cacheKey, "processing", TTL);
        chain.doFilter(request, response);
    }
}