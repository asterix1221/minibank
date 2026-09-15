package com.minibank.common.security;

import com.minibank.auth.entity.Session;
import com.minibank.auth.repository.SessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the X-Session-Token header into an authenticated principal (clientId).
 *
 * Deliberately does NOT reject invalid/missing tokens itself: it just leaves the
 * SecurityContext empty in that case. Whether that matters is decided downstream by
 * the SecurityFilterChain (permitAll vs authenticated()) and the JsonAuthenticationEntryPoint.
 */
public class SessionTokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String SESSION_TOKEN_HEADER = "X-Session-Token";

    private final SessionRepository sessionRepository;

    public SessionTokenAuthenticationFilter(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String headerValue = request.getHeader(SESSION_TOKEN_HEADER);
        if (headerValue != null && !headerValue.isBlank()) {
            try {
                UUID token = UUID.fromString(headerValue.trim());
                Optional<Session> sessionOpt = sessionRepository.findByToken(token);
                if (sessionOpt.isPresent() && sessionOpt.get().isActiveToken()) {
                    Session session = sessionOpt.get();
                    ClientAuthentication authentication =
                            new ClientAuthentication(session.getClientId(), session.getId());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (IllegalArgumentException notAUuid) {
                // malformed token value: leave context unauthenticated, entry point will 401 if required
            }
        }

        filterChain.doFilter(request, response);
    }
}
