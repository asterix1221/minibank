package com.minibank.transaction.service;

import com.minibank.account.entity.Account;
import com.minibank.account.repository.AccountRepository;
import com.minibank.common.exception.InvalidConfirmationCodeException;
import com.minibank.common.exception.TransactionNotFoundException;
import com.minibank.common.security.AccountAccessDeniedException;
import com.minibank.common.security.CurrentClientProvider;
import com.minibank.transaction.dto.ConfirmationRequest;
import com.minibank.transaction.dto.TransactionStatusResponse;
import com.minibank.transaction.entity.Transaction;
import com.minibank.transaction.entity.TransactionStatus;
import com.minibank.transaction.entity.TransactionType;
import com.minibank.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionLifecycleServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionEventRecorder eventRecorder;
    @Mock
    private CurrentClientProvider currentClientProvider;

    private TransactionLifecycleService lifecycle;

    private final UUID clientId = UUID.randomUUID();
    private final UUID fromAccountId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lifecycle = new TransactionLifecycleService(transactionRepository, accountRepository, eventRecorder,
                currentClientProvider, 10L, 15L);
    }

    private Transaction pendingTransaction(String code) {
        Transaction tx = Transaction.createPending(TransactionType.TRANSFER_INTERNAL, clientId, fromAccountId,
                UUID.randomUUID(), null, null, BigDecimal.valueOf(100), BigDecimal.ZERO, code, null);
        tx.setId(UUID.randomUUID());
        return tx;
    }

    @Test
    void confirm_withWrongCode_marksFailedReleasesHoldAndThrows() {
        Transaction tx = pendingTransaction("123456");
        when(transactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
        when(currentClientProvider.getCurrentClientId()).thenReturn(clientId);

        Account account = Account.openNew(clientId, "40817000000000001", "RUB");
        account.hold(BigDecimal.valueOf(100));
        when(accountRepository.findByIdForUpdate(fromAccountId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> lifecycle.confirm(tx.getId(), new ConfirmationRequest("000000")))
                .isInstanceOf(InvalidConfirmationCodeException.class);

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(account.getHeldAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(eventRecorder).record(eq(tx), any(), any());
    }

    @Test
    void confirm_withCorrectCode_marksConfirmed() {
        Transaction tx = pendingTransaction("123456");
        when(transactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
        when(currentClientProvider.getCurrentClientId()).thenReturn(clientId);

        TransactionStatusResponse response = lifecycle.confirm(tx.getId(), new ConfirmationRequest("123456"));

        assertThat(response.status()).isEqualTo(TransactionStatus.CONFIRMED);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.CONFIRMED);
        verify(accountRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void confirm_onAlreadyConfirmedTransaction_isIdempotentAndDoesNotRecheckCode() {
        Transaction tx = pendingTransaction("123456");
        tx.markConfirmed();
        when(transactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
        when(currentClientProvider.getCurrentClientId()).thenReturn(clientId);

        TransactionStatusResponse response = lifecycle.confirm(tx.getId(), new ConfirmationRequest("wrong-code"));

        assertThat(response.status()).isEqualTo(TransactionStatus.CONFIRMED);
        verifyNoInteractions(eventRecorder);
    }

    @Test
    void getOwnedOrThrow_forDifferentClient_throwsAccountAccessDenied() {
        Transaction tx = pendingTransaction("123456");
        when(transactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
        when(currentClientProvider.getCurrentClientId()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> lifecycle.getOwnedOrThrow(tx.getId()))
                .isInstanceOf(AccountAccessDeniedException.class);
    }

    @Test
    void getOwnedOrThrow_unknownId_throwsTransactionNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(transactionRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lifecycle.getOwnedOrThrow(unknownId))
                .isInstanceOf(TransactionNotFoundException.class);
    }
}
