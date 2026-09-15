package com.minibank.account.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "account_number", nullable = false, unique = true)
    private String accountNumber;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "held_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal heldAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static Account openNew(UUID clientId, String accountNumber, String currency) {
        Account account = new Account();
        account.clientId = clientId;
        account.accountNumber = accountNumber;
        account.balance = BigDecimal.ZERO.setScale(2);
        account.heldAmount = BigDecimal.ZERO.setScale(2);
        account.currency = currency;
        return account;
    }

    /** Funds actually movable right now: balance minus whatever is already reserved. */
    public BigDecimal availableBalance() {
        return balance.subtract(heldAmount);
    }

    public void hold(BigDecimal amount) {
        this.heldAmount = this.heldAmount.add(amount);
    }

    public void releaseHold(BigDecimal amount) {
        this.heldAmount = this.heldAmount.subtract(amount);
    }

    public void debit(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
}
