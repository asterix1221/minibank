package com.minibank.common.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

/**
 * Authentication implementation used once a valid {@code X-Session-Token} has been
 * resolved to a client. The principal is the client's UUID - this is what services
 * read via {@link CurrentClientProvider} instead of re-parsing headers.
 */
public class ClientAuthentication extends AbstractAuthenticationToken {

    private final UUID clientId;
    private final UUID sessionId;

    public ClientAuthentication(UUID clientId, UUID sessionId) {
        super(List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
        this.clientId = clientId;
        this.sessionId = sessionId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return clientId;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getSessionId() {
        return sessionId;
    }
}
