package com.minibank.payment.controller;

import com.minibank.payment.dto.PaymentInitiateRequest;
import com.minibank.payment.service.PaymentService;
import com.minibank.transaction.dto.ConfirmationRequest;
import com.minibank.transaction.dto.ReceiptResponse;
import com.minibank.transaction.dto.TransactionInitiateResponse;
import com.minibank.transaction.dto.TransactionStatusResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/templates/{templateId}/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/initiate")
    public TransactionInitiateResponse initiate(@PathVariable UUID templateId,
                                                 @Valid @RequestBody PaymentInitiateRequest request,
                                                 @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return paymentService.initiate(templateId, request, idempotencyKey);
    }

    @PostMapping("/{transactionId}/confirm")
    public TransactionStatusResponse confirm(@PathVariable UUID templateId, @PathVariable UUID transactionId,
                                              @Valid @RequestBody ConfirmationRequest request) {
        return paymentService.confirm(transactionId, request);
    }

    @PostMapping("/{transactionId}/execute")
    public TransactionStatusResponse execute(@PathVariable UUID templateId, @PathVariable UUID transactionId) {
        return paymentService.execute(transactionId);
    }

    @GetMapping("/{transactionId}/receipt")
    public ReceiptResponse receipt(@PathVariable UUID templateId, @PathVariable UUID transactionId) {
        return paymentService.receipt(transactionId);
    }
}
