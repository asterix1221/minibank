package com.minibank.common.correlation;

import com.minibank.common.security.SessionTokenAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Puts sessionId / clientId / transactionId into MDC for the lifetime of the request so
 * every log line (see logging.pattern.console) carries them automatically.
 *
 * Registered AFTER {@link SessionTokenAuthenticationFilter} in the chain so the
 * SecurityContext (and therefore clientId) is already populated by the time this runs.
 *
 * Assumption (see README): "sessionId" in MDC is taken verbatim from the X-Session-Token
 * header value, as the spec literally describes, even though that header technically
 * carries Session.token rather than Session.id. transactionId is extracted as the first
 * UUID-looking path segment, which covers /transfers/{id}/..., /payments/{id}/... etc.
 */
public class MdcCorrelationFilter extends OncePerRequestFilter {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String sessionHeader = request.getHeader(SessionTokenAuthenticationFilter.SESSION_TOKEN_HEADER);
            if (sessionHeader != null && !sessionHeader.isBlank()) {
                MDC.put("sessionId", sessionHeader.trim());
            }

            Object principal = SecurityContextHolder.getContext().getAuthentication() != null
                    ? SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                    : null;
            if (principal instanceof UUID clientId) {
                MDC.put("clientId", clientId.toString());
            }

            String transactionId = extractTransactionId(request.getRequestURI());
            if (transactionId != null) {
                MDC.put("transactionId", transactionId);
            }

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("sessionId");
            MDC.remove("clientId");
            MDC.remove("transactionId");
        }
    }

    private String extractTransactionId(String uri) {
        Matcher matcher = UUID_PATTERN.matcher(uri);
        return matcher.find() ? matcher.group() : null;
    }
}
