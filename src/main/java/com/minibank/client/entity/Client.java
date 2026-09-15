package com.minibank.client.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * NOTE (architecture decision, see README): the spec's field list for Client does not
 * include a place to store the registration verification code. We add
 * registrationCode/registrationCodeExpiresAt directly on Client (mirroring how Session
 * stores its own verificationCode) rather than inventing a separate "Registration" entity,
 * since registrationId == Client.id and there is a strict 1:1 relationship.
 */
@Entity
@Table(name = "clients")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Client {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "phone", nullable = false, unique = true)
    private String phone;

    @Column(name = "passport_data", nullable = false)
    private String passportData;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ClientStatus status;

    @Column(name = "registration_code", length = 10)
    private String registrationCode;

    @Column(name = "registration_code_expires_at")
    private Instant registrationCodeExpiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static Client createPendingVerification(String fullName, String phone, String passportData,
                                                     String registrationCode, Instant codeExpiresAt) {
        Client client = new Client();
        client.fullName = fullName;
        client.phone = phone;
        client.passportData = passportData;
        client.status = ClientStatus.PENDING_VERIFICATION;
        client.registrationCode = registrationCode;
        client.registrationCodeExpiresAt = codeExpiresAt;
        client.createdAt = Instant.now();
        return client;
    }

    public void activate() {
        this.status = ClientStatus.ACTIVE;
        this.registrationCode = null;
        this.registrationCodeExpiresAt = null;
    }
}
