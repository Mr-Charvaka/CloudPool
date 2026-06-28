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
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;

import java.util.Arrays;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:8080}")
    private String[] allowedOrigins;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/api/auth/**", "/public/**", "/", "/index.html", "/favicon.ico", "/static/**", "/error", "/graphiql/**").permitAll()
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .bearerTokenConverter(bearerTokenConverter())
                .authenticationEntryPoint(authenticationEntryPoint())
                .jwt(org.springframework.security.config.Customizer.withDefaults())
            )
            .x509(x509 -> x509.principalExtractor(cert -> cert.getSubjectDN().getName()));

        return http.build();
    }

    @Bean
    public org.springframework.security.web.server.ServerAuthenticationEntryPoint authenticationEntryPoint() {
        return (exchange, e) -> {
            exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            exchange.getResponse().getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            String body = "{\"error\":\"Unauthorized\",\"message\":\"Invalid or expired token\"}";
            org.springframework.core.io.buffer.DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return exchange.getResponse().writeWith(reactor.core.publisher.Mono.just(buffer));
        };
    }

    @Bean
    public org.springframework.security.web.server.authentication.ServerAuthenticationConverter bearerTokenConverter() {
        org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter defaultConverter = 
            new org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter();
        return exchange -> defaultConverter.convert(exchange)
            .switchIfEmpty(reactor.core.publisher.Mono.defer(() -> {
                org.springframework.http.HttpCookie cookie = exchange.getRequest().getCookies().getFirst("cp_token");
                if (cookie != null && cookie.getValue() != null && !cookie.getValue().isEmpty()) {
                    return reactor.core.publisher.Mono.just(new org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken(cookie.getValue()));
                }
                return reactor.core.publisher.Mono.empty();
            }));
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder(@Value("${cloudpool.jwt.secret:${JWT_SECRET}}") String jwtSecret) {
        byte[] keyBytes = jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        javax.crypto.SecretKey secretKey = new SecretKeySpec(keyBytes, "HmacSHA512");
        return NimbusReactiveJwtDecoder.withSecretKey(secretKey).build();
    }

    @Bean
    public ReactiveUserDetailsService userDetailsService() {
        return username -> Mono.just(User.withUsername(username).password("").authorities("USER").build());
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
        if (hasWildcard) {
            throw new IllegalArgumentException("Wildcard CORS origins are not allowed for security reasons");
        }
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
