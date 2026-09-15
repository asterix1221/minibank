package com.minibank.transfer.service;

import com.minibank.account.entity.Account;
import com.minibank.account.repository.AccountRepository;
import com.minibank.common.exception.BusinessRuleViolationException;
import com.minibank.common.exception.InsufficientFundsException;
import com.minibank.common.metrics.BusinessMetrics;
import com.minibank.common.security.AccountAccessDeniedException;
import com.minibank.common.security.CurrentClientProvider;
import com.minibank.transaction.entity.TransactionType;
import com.minibank.transaction.repository.TransactionRepository;
import com.minibank.transaction.service.TransactionEventRecorder;
import com.minibank.transaction.service.TransactionLifecycleService;
import com.minibank.transfer.dto.InternalTransferInitiateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalTransferServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionEventRecorder eventRecorder;
    @Mock
    private TransactionLifecycleService lifecycle;
    @Mock
    private CurrentClientProvider currentClientProvider;
    @Mock
    private BusinessMetrics businessMetrics;
    @Mock
    private TransactionRepository transactionRepository;

    private InternalTransferService service;

    private final UUID clientId = UUID.randomUUID();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new InternalTransferService(accountRepository, eventRecorder, lifecycle, currentClientProvider,
                businessMetrics, transactionRepository);
        lenient().when(lifecycle.findExistingByIdempotencyKey(any())).thenReturn(Optional.empty());
        lenient().when(currentClientProvider.getCurrentClientId()).thenReturn(clientId);
        // pass the timed operation straight through, as the real BusinessMetrics would
        lenient().when(businessMetrics.timed(anyString(), any(TransactionType.class), any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get());
    }

    @Test
    void initiate_withInsufficientBalance_throws() {
        Account from = Account.openNew(clientId, "40817000000000001", "RUB");
        from.setId(UUID.randomUUID());
        Account to = Account.openNew(UUID.randomUUID(), "40817000000000002", "RUB");
        to.setId(UUID.randomUUID());

        when(accountRepository.findById(from.getId())).thenReturn(Optional.of(from));
        when(accountRepository.findByAccountNumber(to.getAccountNumber())).thenReturn(Optional.of(to));
        when(accountRepository.findByIdForUpdate(from.getId())).thenReturn(Optional.of(from));

        InternalTransferInitiateRequest request =
                new InternalTransferInitiateRequest(from.getId(), to.getAccountNumber(), BigDecimal.valueOf(1000));

        assertThatThrownBy(() -> service.initiate(request, null))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void initiate_toSameAccount_throwsBusinessRuleViolation() {
        Account from = Account.openNew(clientId, "40817000000000001", "RUB");
        from.setId(UUID.randomUUID());
        from.setBalance(BigDecimal.valueOf(1000));

        when(accountRepository.findById(from.getId())).thenReturn(Optional.of(from));
        when(accountRepository.findByAccountNumber(from.getAccountNumber())).thenReturn(Optional.of(from));

        InternalTransferInitiateRequest request =
                new InternalTransferInitiateRequest(from.getId(), from.getAccountNumber(), BigDecimal.valueOf(10));

        assertThatThrownBy(() -> service.initiate(request, null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void initiate_onForeignAccount_throwsAccountAccessDenied() {
        Account from = Account.openNew(UUID.randomUUID(), "40817000000000001", "RUB");
        from.setId(UUID.randomUUID());
        when(accountRepository.findById(from.getId())).thenReturn(Optional.of(from));

        InternalTransferInitiateRequest request =
                new InternalTransferInitiateRequest(from.getId(), "40817000000000099", BigDecimal.valueOf(10));

        assertThatThrownBy(() -> service.initiate(request, null))
                .isInstanceOf(AccountAccessDeniedException.class);
    }
}
