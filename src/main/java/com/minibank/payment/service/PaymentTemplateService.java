package com.minibank.payment.service;

import com.minibank.common.exception.EntityNotFoundBusinessException;
import com.minibank.common.security.AccountAccessDeniedException;
import com.minibank.common.security.CurrentClientProvider;
import com.minibank.payment.dto.CreatePaymentTemplateRequest;
import com.minibank.payment.dto.PaymentTemplateResponse;
import com.minibank.payment.entity.PaymentTemplate;
import com.minibank.payment.repository.PaymentTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PaymentTemplateService {

    private final PaymentTemplateRepository templateRepository;
    private final CurrentClientProvider currentClientProvider;

    public PaymentTemplateService(PaymentTemplateRepository templateRepository,
                                   CurrentClientProvider currentClientProvider) {
        this.templateRepository = templateRepository;
        this.currentClientProvider = currentClientProvider;
    }

    @Transactional
    public PaymentTemplateResponse create(CreatePaymentTemplateRequest request) {
        UUID clientId = currentClientProvider.getCurrentClientId();
        PaymentTemplate template = PaymentTemplate.create(clientId, request.name(), request.recipientDetails(),
                request.defaultAmount(), request.category());
        template = templateRepository.save(template);
        return toResponse(template);
    }

    @Transactional(readOnly = true)
    public List<PaymentTemplateResponse> list() {
        UUID clientId = currentClientProvider.getCurrentClientId();
        return templateRepository.findByClientId(clientId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PaymentTemplateResponse get(UUID templateId) {
        return toResponse(getOwnedOrThrow(templateId));
    }

    public PaymentTemplate getOwnedOrThrow(UUID templateId) {
        PaymentTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new EntityNotFoundBusinessException("TEMPLATE_NOT_FOUND", "Payment template not found"));
        UUID clientId = currentClientProvider.getCurrentClientId();
        if (!template.getClientId().equals(clientId)) {
            throw new AccountAccessDeniedException("Payment template does not belong to the current client");
        }
        return template;
    }

    private PaymentTemplateResponse toResponse(PaymentTemplate template) {
        return new PaymentTemplateResponse(template.getId(), template.getName(), template.getRecipientDetails(),
                template.getDefaultAmount(), template.getCategory());
    }
}
