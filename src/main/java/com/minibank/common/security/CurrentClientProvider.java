package com.minibank.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Single place services use to find out "who is calling right now", instead of
 * every service re-parsing the X-Session-Token header itself.
 */
@Component
public class CurrentClientProvider {

    public UUID getCurrentClientId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UUID clientId)) {
            throw new IllegalStateException("No authenticated client in security context");
        }
        return clientId;
    }

    public UUID getCurrentSessionId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof ClientAuthentication clientAuthentication) {
            return clientAuthentication.getSessionId();
        }
        throw new IllegalStateException("No authenticated session in security context");
    }
}
