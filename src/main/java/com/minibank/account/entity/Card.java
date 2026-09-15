package com.minibank.account.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "cards")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Card {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "card_number", nullable = false, unique = true)
    private String cardNumber;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CardStatus status;

    public static Card issueNew(UUID accountId, String cardNumber, LocalDate expiryDate) {
        Card card = new Card();
        card.accountId = accountId;
        card.cardNumber = cardNumber;
        card.expiryDate = expiryDate;
        card.status = CardStatus.ACTIVE;
        return card;
    }

    /** e.g. 4276 **** **** 1234 - never expose the full PAN in API responses. */
    public String maskedNumber() {
        String digitsOnly = cardNumber.replaceAll("\\s", "");
        String first4 = digitsOnly.substring(0, 4);
        String last4 = digitsOnly.substring(digitsOnly.length() - 4);
        return first4 + " **** **** " + last4;
    }
}
