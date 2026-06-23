package com.cloudpool.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.adapter.ForwardedHeaderTransformer;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import reactor.core.publisher.Mono;

import java.util.Arrays;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:8080}")
    private String[] allowedOrigins;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
                .requireCsrfProtectionMatcher(exchange -> {
                    String path = exchange.getRequest().getURI().getPath();
                    boolean requiresProtection = !path.startsWith("/api/auth/") && !path.startsWith("/public/") && !path.equals("/oauth/callback");
                    return requiresProtection ? ServerWebExchangeMatcher.MatchResult.match() : ServerWebExchangeMatcher.MatchResult.notMatch();
                })
            )
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/api/auth/**", "/public/**").permitAll()
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(ServerHttpSecurity.OAuth2ResourceServerSpec::jwt)
            .x509(x509 -> x509.principalExtractor(cert -> cert.getSubjectDN().getName()));

        return http.build();
    }

    @Bean
    public ForwardedHeaderTransformer forwardedHeaderTransformer() {
        // Configure ForwardedHeaderFilter securely
        return new ForwardedHeaderTransformer();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        java.util.List<String> originsList = Arrays.asList(allowedOrigins);
        boolean hasWildcard = originsList.contains("*") || originsList.contains(".*");
        config.setAllowedOriginPatterns(originsList);
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-API-KEY", "X-Project-Id", "X-XSRF-TOKEN"));
        // DO NOT expose Authorization headers globally
        config.setAllowCredentials(!hasWildcard);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
