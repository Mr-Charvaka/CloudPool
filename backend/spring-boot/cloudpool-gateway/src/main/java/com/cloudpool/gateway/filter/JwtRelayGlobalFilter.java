package com.cloudpool.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtRelayGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .flatMap(principal -> {
                    if (principal instanceof JwtAuthenticationToken) {
                        JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) principal;
                        // The user's email is usually in the "sub" or "email" claim. 
                        // Our JwtUtils sets email in the subject.
                        String email = jwtAuth.getToken().getSubject();
                        if (email == null) {
                            email = jwtAuth.getToken().getClaimAsString("email");
                        }
                        
                        if (email != null) {
                            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                                    .header("X-User-Email", email)
                                    .build();
                            return chain.filter(exchange.mutate().request(mutatedRequest).build());
                        }
                    }
                    return chain.filter(exchange);
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
