package com.minibank.auth.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Session {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "phone", nullable = false)
    private String phone;

    /** Not returned in the open after confirmation - see SessionResponse mapping. */
    @Column(name = "verification_code", nullable = false, length = 10)
    private String verificationCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SessionStatus status;

    /** Issued only once status becomes CONFIRMED. This is the value X-Session-Token carries. */
    @Column(name = "token", unique = true)
    private UUID token;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public static Session initiate(UUID clientId, String phone, String verificationCode, Instant expiresAt) {
        Session session = new Session();
        session.clientId = clientId;
        session.phone = phone;
        session.verificationCode = verificationCode;
        session.status = SessionStatus.PENDING_CODE;
        session.createdAt = Instant.now();
        session.expiresAt = expiresAt;
        return session;
    }

    public boolean isCodeExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isActiveToken() {
        return status == SessionStatus.CONFIRMED && token != null && Instant.now().isBefore(expiresAt);
    }

    /** Confirms the login code and issues the bearer token, extending expiry to a full session lifetime. */
    public void confirm(Instant tokenExpiresAt) {
        this.status = SessionStatus.CONFIRMED;
        this.token = UUID.randomUUID();
        this.expiresAt = tokenExpiresAt;
    }
}
