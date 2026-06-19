package com.cloudpool.security;

import com.cloudpool.model.ApiKey;
import com.cloudpool.model.User;
import com.cloudpool.repository.ApiKeyRepository;
import com.cloudpool.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.cloudpool.service.ApiKeyUsageService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyUsageService apiKeyUsageService;
    private final com.cloudpool.service.CacheService cacheService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String jwt = parseJwt(request);
            if (jwt != null && !cacheService.isTokenBlacklisted(jwt) && jwtUtils.validateToken(jwt)) {
                String email = jwtUtils.getEmailFromToken(jwt);
                Optional<User> userOpt = userRepository.findByEmail(email);

                if (userOpt.isPresent() && userOpt.get().isActive()) {
                    User user = userOpt.get();
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            user, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } else {
                // Check X-API-KEY header
                String apiKeyRaw = request.getHeader("X-API-KEY");
                if (StringUtils.hasText(apiKeyRaw)) {
                    String hashedKey = hashApiKey(apiKeyRaw);
                    Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByKeyHashWithUser(hashedKey);

                    if (apiKeyOpt.isPresent() && apiKeyOpt.get().isActive()) {
                        ApiKey apiKey = apiKeyOpt.get();
                        if (apiKey.getExpiresAt() == null || apiKey.getExpiresAt().isAfter(LocalDateTime.now())) {
                            User user = apiKey.getUser();
                            
                            // Update last used timestamp
                            apiKey.setLastUsedAt(LocalDateTime.now());
                            apiKeyRepository.save(apiKey);

                            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                    user, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                            
                            // Save key ID to request context to log usage after filter runs
                            request.setAttribute("authenticatedApiKeyId", apiKey.getId());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Cannot set user authentication: {}", e);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            Object apiKeyIdAttr = request.getAttribute("authenticatedApiKeyId");
            if (apiKeyIdAttr instanceof UUID) {
                try {
                    apiKeyUsageService.logUsage(
                            (UUID) apiKeyIdAttr,
                            request.getRequestURI(),
                            request.getMethod(),
                            response.getStatus(),
                            request.getRemoteAddr()
                    );
                } catch (Exception e) {
                    logger.error("Failed to log API key usage: " + e.getMessage());
                }
            }
        }
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }

    private String hashApiKey(String apiKeyRaw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKeyRaw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new com.cloudpool.exception.CloudPoolException("SHA-256 algorithm not available", e);
        }
    }
}

