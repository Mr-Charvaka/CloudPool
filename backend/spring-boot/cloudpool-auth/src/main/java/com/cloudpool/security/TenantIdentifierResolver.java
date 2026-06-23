package com.cloudpool.security;

import com.cloudpool.model.User;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver {

    @Override
    public String resolveCurrentTenantIdentifier() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user.getId().toString();
        }
        return "00000000-0000-0000-0000-000000000000";
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
