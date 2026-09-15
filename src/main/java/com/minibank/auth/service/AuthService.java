package com.minibank.auth.service;

import com.minibank.account.util.VerificationCodeGenerator;
import com.minibank.auth.dto.LoginConfirmRequest;
import com.minibank.auth.dto.LoginConfirmResponse;
import com.minibank.auth.dto.LoginInitiateRequest;
import com.minibank.auth.dto.LoginInitiateResponse;
import com.minibank.auth.entity.Session;
import com.minibank.auth.entity.SessionStatus;
import com.minibank.auth.repository.SessionRepository;
import com.minibank.client.entity.Client;
import com.minibank.client.entity.ClientStatus;
import com.minibank.client.repository.ClientRepository;
import com.minibank.common.exception.BusinessRuleViolationException;
import com.minibank.common.exception.ClientNotFoundException;
import com.minibank.common.exception.EntityNotFoundBusinessException;
import com.minibank.common.exception.InvalidConfirmationCodeException;
import com.minibank.common.exception.InvalidTransactionStateException;
import com.minibank.common.metrics.BusinessMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final SessionRepository sessionRepository;
    private final ClientRepository clientRepository;
    private final BusinessMetrics businessMetrics;
    private final long loginCodeTtlMinutes;
    private final long sessionTtlMinutes;

    public AuthService(SessionRepository sessionRepository,
                        ClientRepository clientRepository,
                        BusinessMetrics businessMetrics,
                        @Value("${minibank.verification.login-code-ttl-minutes}") long loginCodeTtlMinutes,
                        @Value("${minibank.security.session-ttl-minutes}") long sessionTtlMinutes) {
        this.sessionRepository = sessionRepository;
        this.clientRepository = clientRepository;
        this.businessMetrics = businessMetrics;
        this.loginCodeTtlMinutes = loginCodeTtlMinutes;
        this.sessionTtlMinutes = sessionTtlMinutes;
    }

    @Transactional
    public LoginInitiateResponse initiate(LoginInitiateRequest request) {
        Client client = clientRepository.findByPhone(request.phone())
                .orElseThrow(() -> new ClientNotFoundException("No client registered with this phone"));

        if (client.getStatus() != ClientStatus.ACTIVE) {
            throw new BusinessRuleViolationException("CLIENT_NOT_ACTIVE",
                    "Client registration is not completed yet");
        }

        String code = VerificationCodeGenerator.generate();
        Instant expiresAt = Instant.now().plus(loginCodeTtlMinutes, ChronoUnit.MINUTES);
        Session session = Session.initiate(client.getId(), client.getPhone(), code, expiresAt);
        session = sessionRepository.save(session);

        log.info("Login initiated for clientId={}, sessionId={}, code={}", client.getId(), session.getId(), code);
        return new LoginInitiateResponse(session.getId(), code);
    }

    @Transactional
    public LoginConfirmResponse confirm(LoginConfirmRequest request) {
        Session session = sessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new EntityNotFoundBusinessException("SESSION_NOT_FOUND", "Session not found"));

        if (session.getStatus() == SessionStatus.CONFIRMED) {
            // idempotent: confirming an already-confirmed session just returns the existing token
            return new LoginConfirmResponse(session.getToken(), session.getExpiresAt());
        }

        if (session.getStatus() != SessionStatus.PENDING_CODE) {
            throw new InvalidTransactionStateException("Session is not awaiting code confirmation");
        }

        if (session.isCodeExpired()) {
            session.setStatus(SessionStatus.EXPIRED);
            businessMetrics.recordAuthAttempt("failure");
            throw new InvalidConfirmationCodeException("Login code has expired, please initiate login again");
        }

        if (!session.getVerificationCode().equals(request.code())) {
            businessMetrics.recordAuthAttempt("failure");
            throw new InvalidConfirmationCodeException("Provided code does not match");
        }

        Instant tokenExpiresAt = Instant.now().plus(sessionTtlMinutes, ChronoUnit.MINUTES);
        session.confirm(tokenExpiresAt);
        businessMetrics.recordAuthAttempt("success");
        log.info("Login confirmed for sessionId={}, clientId={}", session.getId(), session.getClientId());
        return new LoginConfirmResponse(session.getToken(), session.getExpiresAt());
    }
}
