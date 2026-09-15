package com.minibank.client.service;

import com.minibank.account.entity.Account;
import com.minibank.account.entity.Card;
import com.minibank.account.repository.AccountRepository;
import com.minibank.account.repository.CardRepository;
import com.minibank.account.util.AccountNumberGenerator;
import com.minibank.account.util.CardNumberGenerator;
import com.minibank.account.util.VerificationCodeGenerator;
import com.minibank.client.dto.ConfirmRegistrationRequest;
import com.minibank.client.dto.ConfirmRegistrationResponse;
import com.minibank.client.dto.RegisterRequest;
import com.minibank.client.dto.RegisterResponse;
import com.minibank.client.entity.Client;
import com.minibank.client.entity.ClientStatus;
import com.minibank.client.repository.ClientRepository;
import com.minibank.common.exception.BusinessRuleViolationException;
import com.minibank.common.exception.ClientNotFoundException;
import com.minibank.common.exception.InvalidConfirmationCodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class ClientRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(ClientRegistrationService.class);
    private static final String DEFAULT_CURRENCY = "RUB";
    private static final int CARD_VALIDITY_YEARS = 4;

    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;
    private final CardRepository cardRepository;
    private final long registrationCodeTtlMinutes;

    public ClientRegistrationService(ClientRepository clientRepository,
                                      AccountRepository accountRepository,
                                      CardRepository cardRepository,
                                      @Value("${minibank.verification.registration-code-ttl-minutes}") long registrationCodeTtlMinutes) {
        this.clientRepository = clientRepository;
        this.accountRepository = accountRepository;
        this.cardRepository = cardRepository;
        this.registrationCodeTtlMinutes = registrationCodeTtlMinutes;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (clientRepository.existsByPhone(request.phone())) {
            throw new BusinessRuleViolationException("PHONE_ALREADY_REGISTERED",
                    "A client with this phone number already exists");
        }

        String code = VerificationCodeGenerator.generate();
        Instant expiresAt = Instant.now().plus(registrationCodeTtlMinutes, ChronoUnit.MINUTES);
        Client client = Client.createPendingVerification(
                request.fullName(), request.phone(), request.passportData(), code, expiresAt);
        client = clientRepository.save(client);

        log.info("Registration initiated for clientId={}, code={}", client.getId(), code);
        return new RegisterResponse(client.getId(), code);
    }

    @Transactional
    public ConfirmRegistrationResponse confirmRegistration(UUID registrationId, ConfirmRegistrationRequest request) {
        Client client = clientRepository.findById(registrationId)
                .orElseThrow(() -> new ClientNotFoundException("Registration not found"));

        if (client.getStatus() == ClientStatus.ACTIVE) {
            // idempotent: registration already completed, hand back the existing account/card
            return existingConfirmation(client);
        }

        if (client.getRegistrationCodeExpiresAt() == null || Instant.now().isAfter(client.getRegistrationCodeExpiresAt())) {
            throw new InvalidConfirmationCodeException("Registration code has expired, please register again");
        }

        if (!client.getRegistrationCode().equals(request.code())) {
            throw new InvalidConfirmationCodeException("Provided code does not match");
        }

        Account account = Account.openNew(client.getId(), generateUniqueAccountNumber(), DEFAULT_CURRENCY);
        account = accountRepository.save(account);

        Card card = Card.issueNew(account.getId(), generateUniqueCardNumber(), LocalDate.now().plusYears(CARD_VALIDITY_YEARS));
        card = cardRepository.save(card);

        client.activate();

        log.info("Registration confirmed for clientId={}, accountId={}", client.getId(), account.getId());
        return new ConfirmRegistrationResponse(client.getId(), account.getAccountNumber(), card.maskedNumber());
    }

    private ConfirmRegistrationResponse existingConfirmation(Client client) {
        List<Account> accounts = accountRepository.findByClientId(client.getId());
        Account account = accounts.stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Active client without an account: " + client.getId()));
        List<Card> cards = cardRepository.findByAccountId(account.getId());
        Card card = cards.stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Account without a card: " + account.getId()));
        return new ConfirmRegistrationResponse(client.getId(), account.getAccountNumber(), card.maskedNumber());
    }

    private String generateUniqueAccountNumber() {
        String candidate;
        do {
            candidate = AccountNumberGenerator.generate();
        } while (accountRepository.existsByAccountNumber(candidate));
        return candidate;
    }

    private String generateUniqueCardNumber() {
        String candidate;
        do {
            candidate = CardNumberGenerator.generate();
        } while (cardRepository.existsByCardNumber(candidate));
        return candidate;
    }
}
