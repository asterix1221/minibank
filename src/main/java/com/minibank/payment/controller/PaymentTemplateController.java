package com.minibank.payment.controller;

import com.minibank.payment.dto.CreatePaymentTemplateRequest;
import com.minibank.payment.dto.PaymentTemplateResponse;
import com.minibank.payment.service.PaymentTemplateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/templates")
public class PaymentTemplateController {

    private final PaymentTemplateService templateService;

    public PaymentTemplateController(PaymentTemplateService templateService) {
        this.templateService = templateService;
    }

    @PostMapping
    public PaymentTemplateResponse create(@Valid @RequestBody CreatePaymentTemplateRequest request) {
        return templateService.create(request);
    }

    @GetMapping
    public List<PaymentTemplateResponse> list() {
        return templateService.list();
    }

    @GetMapping("/{templateId}")
    public PaymentTemplateResponse get(@PathVariable UUID templateId) {
        return templateService.get(templateId);
    }
}
