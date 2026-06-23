package com.cloudpool.config;

import com.cloudpool.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import com.cloudpool.filter.TenantFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final com.cloudpool.filter.LoginRateLimiterFilter loginRateLimiterFilter;
    private final com.cloudpool.filter.RateLimitFilter rateLimitFilter;
    private final com.cloudpool.filter.GraphQLRateLimitFilter graphQLRateLimitFilter;

    @Value("${cloudpool.cors.allowed-origins:http://localhost:3000,http://localhost:8080}")
    private String allowedOrigins;

    @Value("${spring.profiles.active:prod}")
    private String activeProfile;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        boolean isDev = "dev".equalsIgnoreCase(activeProfile);

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf
                .csrfTokenRepository(org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers(
                    AntPathRequestMatcher.antMatcher("/api/auth/login"),
                    AntPathRequestMatcher.antMatcher("/api/auth/register"),
                    AntPathRequestMatcher.antMatcher("/api/auth/refresh"),
                    AntPathRequestMatcher.antMatcher("/api/auth/refresh-cookie"),
                    AntPathRequestMatcher.antMatcher("/api/auth/csrf"),
                    AntPathRequestMatcher.antMatcher("/api/files/shared/**")
                )
            );

        if (isDev) {
            http.csrf(csrf -> csrf.ignoringRequestMatchers(AntPathRequestMatcher.antMatcher("/h2-console/**")));
            http.headers(headers -> headers.frameOptions(frame -> frame.disable()));
        }

        http.authorizeHttpRequests(auth -> {
            // Actuator
            auth.requestMatchers(AntPathRequestMatcher.antMatcher("/actuator/health")).permitAll();
            if (isDev) {
                auth.requestMatchers(AntPathRequestMatcher.antMatcher("/actuator/**")).permitAll();
                auth.requestMatchers(AntPathRequestMatcher.antMatcher("/h2-console/**")).permitAll();
            }

            auth
                .requestMatchers(AntPathRequestMatcher.antMatcher("/api/health")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/graphql")).authenticated()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/graphiql/**")).hasRole("ADMIN")
                .requestMatchers(AntPathRequestMatcher.antMatcher("/api/auth/**")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/api/files/shared/**")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/oauth/callback")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/index.html")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/favicon.ico")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/static/**")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/error")).permitAll()
                .anyRequest().authenticated();
        })
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(graphQLRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(loginRateLimiterFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new TenantFilter(), JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        boolean hasWildcard = origins.contains("*") || origins.contains(".*");
        configuration.setAllowedOriginPatterns(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-API-KEY", "X-Project-Id"));
        configuration.setExposedHeaders(Arrays.asList("Authorization"));
        configuration.setAllowCredentials(!hasWildcard);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
