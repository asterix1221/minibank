package com.minibank.payment.service;

import com.minibank.common.exception.EntityNotFoundBusinessException;
import com.minibank.common.security.AccountAccessDeniedException;
import com.minibank.common.security.CurrentClientProvider;
import com.minibank.payment.entity.PaymentTemplate;
import com.minibank.payment.repository.PaymentTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentTemplateServiceTest {

    @Mock
    private PaymentTemplateRepository templateRepository;
    @Mock
    private CurrentClientProvider currentClientProvider;

    private PaymentTemplateService service;

    @BeforeEach
    void setUp() {
        service = new PaymentTemplateService(templateRepository, currentClientProvider);
    }

    @Test
    void getOwnedOrThrow_forAnotherClientsTemplate_throwsAccountAccessDenied() {
        UUID templateId = UUID.randomUUID();
        PaymentTemplate template = PaymentTemplate.create(UUID.randomUUID(), "Utilities", "acc:123", null, "UTILITIES");
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(currentClientProvider.getCurrentClientId()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> service.getOwnedOrThrow(templateId))
                .isInstanceOf(AccountAccessDeniedException.class);
    }

    @Test
    void getOwnedOrThrow_unknownTemplate_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(templateRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOwnedOrThrow(id))
                .isInstanceOf(EntityNotFoundBusinessException.class);
    }
}
