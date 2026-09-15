package com.minibank.account.service;

import com.minibank.account.dto.AccountSummaryResponse;
import com.minibank.account.entity.Account;
import com.minibank.account.repository.AccountRepository;
import com.minibank.common.exception.AccountNotFoundException;
import com.minibank.common.security.AccountAccessDeniedException;
import com.minibank.common.security.CurrentClientProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final CurrentClientProvider currentClientProvider;

    public AccountService(AccountRepository accountRepository, CurrentClientProvider currentClientProvider) {
        this.accountRepository = accountRepository;
        this.currentClientProvider = currentClientProvider;
    }

    @Transactional(readOnly = true)
    public List<AccountSummaryResponse> myAccounts() {
        UUID clientId = currentClientProvider.getCurrentClientId();
        return accountRepository.findByClientId(clientId).stream().map(this::toResponse).toList();
    }

    /**
     * NOTE (architecture decision, see README): the spec never defines any way to bring money
     * into the system from outside - a freshly registered account always starts at balance 0,
     * and every other endpoint only ever moves money that's already in the system between
     * existing accounts. Without something like this, no transfer or payment could ever be
     * tested end-to-end through the API alone (this is exactly what broke the Postman run and
     * the smoke-test.sh happy path). Deliberately named/scoped as a test convenience - it just
     * credits the caller's own account, no real "external deposit" concept exists elsewhere in
     * the domain.
     */
    @Transactional
    public AccountSummaryResponse deposit(UUID accountId, BigDecimal amount) {
        UUID clientId = currentClientProvider.getCurrentClientId();
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        if (!account.getClientId().equals(clientId)) {
            throw new AccountAccessDeniedException("Account does not belong to the current client");
        }
        account.credit(amount);
        accountRepository.save(account);
        return toResponse(account);
    }

    private AccountSummaryResponse toResponse(Account account) {
        return new AccountSummaryResponse(account.getId(), account.getAccountNumber(),
                account.getBalance(), account.getCurrency());
    }
}
