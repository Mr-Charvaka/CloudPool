package com.cloudpool.security;

import com.cloudpool.model.User;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user.getId();
        }
        // Fallback or system tenant. For strict multitenancy, you might throw an exception,
        // but Spring Security might load contexts where tenant isn't set yet.
        return UUID.fromString("00000000-0000-0000-0000-000000000000"); 
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true; // Enforce tenant boundaries on existing sessions
    }
}
