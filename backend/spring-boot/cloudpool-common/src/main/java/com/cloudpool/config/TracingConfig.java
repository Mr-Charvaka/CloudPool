package com.cloudpool.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConditionalOnClass(name = "io.micrometer.tracing.Tracer")
public class TracingConfig {

    @Bean
    public Filter traceIdFilter(Tracer tracer) {
        return (ServletRequest request, ServletResponse response, FilterChain chain) -> {
            Span span = tracer.currentSpan();
            if (span != null) {
                String traceId = span.context().traceId();
                String spanId = span.context().spanId();
                MDC.put("traceId", traceId);
                MDC.put("spanId", spanId);
                if (response instanceof HttpServletResponse httpResponse) {
                    httpResponse.setHeader("X-Trace-Id", traceId);
                }
            }
            try {
                chain.doFilter(request, response);
            } finally {
                MDC.remove("traceId");
                MDC.remove("spanId");
            }
        };
    }
}