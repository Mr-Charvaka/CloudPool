package com.cloudpool.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

@Component
@Order(3)
public class RequestLoggingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        Instant start = Instant.now();
        chain.doFilter(request, response);
        long elapsed = Duration.between(start, Instant.now()).toMillis();

        String traceId = MDC.get("traceId");
        if (traceId == null) traceId = "unknown";

        log.info("{} {} {} {}ms [traceId={}]",
                req.getMethod(), req.getRequestURI(), res.getStatus(), elapsed, traceId);
    }
}