package com.cloudpool.filter;

import com.cloudpool.model.WafRule;
import com.cloudpool.repository.WafRuleRepository;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class WafFilter extends OncePerRequestFilter {

    private final WafRuleRepository wafRuleRepository;
    
    // In-memory rate limiting cache: "projectId:ip" -> RateLimiter
    private final LoadingCache<String, RateLimiter> limiters = CacheBuilder.newBuilder()
        .maximumSize(10000)
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build(new CacheLoader<>() {
            @Override
            public RateLimiter load(String key) {
                // Default to 10 requests per second if not specified
                return RateLimiter.create(10.0);
            }
        });

    // Basic SQLi regex pattern
    private static final Pattern SQLI_PATTERN = Pattern.compile(
        "(?i).*\\b(SELECT|INSERT|UPDATE|DELETE|DROP|ALTER|UNION|OR|AND)\\b.*"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String projectIdStr = request.getHeader("X-Project-Id");
        
        if (projectIdStr != null && !projectIdStr.isBlank()) {
            try {
                UUID projectId = UUID.fromString(projectIdStr);
                List<WafRule> rules = wafRuleRepository.findByProjectIdAndIsActiveTrue(projectId);
                
                String clientIp = getClientIp(request);
                String requestUri = request.getRequestURI();
                String queryString = request.getQueryString() != null ? request.getQueryString() : "";

                for (WafRule rule : rules) {
                    // 1. IP Blocking
                    if ("IP_BLOCK".equalsIgnoreCase(rule.getRuleType())) {
                        if (clientIp.equals(rule.getPattern())) {
                            handleBlock(response, rule, "IP address blocked");
                            return;
                        }
                    }

                    // 2. Rate Limiting
                    if ("RATE_LIMIT".equalsIgnoreCase(rule.getRuleType())) {
                        double rps = Double.parseDouble(rule.getPattern());
                        String bucketKey = projectId.toString() + ":" + clientIp;
                        
                        RateLimiter rateLimiter = limiters.get(bucketKey);
                        // Update rate if rule changed (simplified: we just check if it's close, else recreate)
                        if (Math.abs(rateLimiter.getRate() - rps) > 0.1) {
                            rateLimiter.setRate(rps);
                        }
                        
                        if (!rateLimiter.tryAcquire(1, 100, TimeUnit.MILLISECONDS)) {
                            response.setStatus(429);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\": \"WAF: Rate limit exceeded\"}");
                            return;
                        }
                    }

                    // 3. Basic SQL Injection Block
                    if ("SQLI_BLOCK".equalsIgnoreCase(rule.getRuleType())) {
                        if (SQLI_PATTERN.matcher(requestUri).matches() || SQLI_PATTERN.matcher(queryString).matches()) {
                            handleBlock(response, rule, "Potential SQL Injection detected");
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("WAF Filter error: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private void handleBlock(HttpServletResponse response, WafRule rule, String reason) throws IOException {
        log.warn("WAF Blocked Request: Project {}, Rule {}, Reason: {}", rule.getProjectId(), rule.getRuleType(), reason);
        response.setStatus(403);
        response.setContentType("application/json");
        response.getWriter().write(String.format("{\"error\": \"WAF Blocked: %s\"}", reason));
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isBlank()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
