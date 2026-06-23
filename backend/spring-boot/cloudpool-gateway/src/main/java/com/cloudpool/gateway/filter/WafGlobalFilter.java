package com.cloudpool.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.regex.Pattern;

@Component
public class WafGlobalFilter implements GlobalFilter, Ordered {

    private static final Pattern XSS_PATTERN = Pattern.compile("(<script>|%3Cscript%3E|javascript:)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SQLI_PATTERN = Pattern.compile("(?i)(union\\s+select|select\\s+.*\\s+from|drop\\s+table|insert\\s+into|update\\s+.*\\s+set)");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String uri = java.net.URLDecoder.decode(exchange.getRequest().getURI().toString(), java.nio.charset.StandardCharsets.UTF_8);
        
        if (XSS_PATTERN.matcher(uri).find() || SQLI_PATTERN.matcher(uri).find()) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }
        
        // Also check headers
        for (java.util.List<String> headerValues : exchange.getRequest().getHeaders().values()) {
            for (String val : headerValues) {
                if (XSS_PATTERN.matcher(val).find() || SQLI_PATTERN.matcher(val).find()) {
                    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                    return exchange.getResponse().setComplete();
                }
            }
        }
        
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -100; // Run early
    }
}
