package com.minibank.transfer.controller;

import com.minibank.transaction.dto.ConfirmationRequest;
import com.minibank.transaction.dto.ReceiptResponse;
import com.minibank.transaction.dto.TransactionInitiateResponse;
import com.minibank.transaction.dto.TransactionStatusResponse;
import com.minibank.transfer.dto.ExternalTransferInitiateRequest;
import com.minibank.transfer.dto.InternalTransferInitiateRequest;
import com.minibank.transfer.service.ExternalTransferService;
import com.minibank.transfer.service.InternalTransferService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final InternalTransferService internalTransferService;
    private final ExternalTransferService externalTransferService;

    public TransferController(InternalTransferService internalTransferService,
                               ExternalTransferService externalTransferService) {
        this.internalTransferService = internalTransferService;
        this.externalTransferService = externalTransferService;
    }

    @PostMapping("/internal/initiate")
    public TransactionInitiateResponse initiateInternal(@Valid @RequestBody InternalTransferInitiateRequest request,
                                                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return internalTransferService.initiate(request, idempotencyKey);
    }

    @PostMapping("/internal/{transactionId}/confirm")
    public TransactionStatusResponse confirmInternal(@PathVariable UUID transactionId,
                                                       @Valid @RequestBody ConfirmationRequest request) {
        return internalTransferService.confirm(transactionId, request);
    }

    @PostMapping("/internal/{transactionId}/execute")
    public TransactionStatusResponse executeInternal(@PathVariable UUID transactionId) {
        return internalTransferService.execute(transactionId);
    }

    @PostMapping("/external/initiate")
    public TransactionInitiateResponse initiateExternal(@Valid @RequestBody ExternalTransferInitiateRequest request,
                                                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return externalTransferService.initiate(request, idempotencyKey);
    }

    @PostMapping("/external/{transactionId}/confirm")
    public TransactionStatusResponse confirmExternal(@PathVariable UUID transactionId,
                                                       @Valid @RequestBody ConfirmationRequest request) {
        return externalTransferService.confirm(transactionId, request);
    }

    @PostMapping("/external/{transactionId}/execute")
    public TransactionStatusResponse executeExternal(@PathVariable UUID transactionId) {
        return externalTransferService.execute(transactionId);
    }

    /**
     * Receipt (current-state view) works for both internal and external transfers -
     * both InternalTransferService and ExternalTransferService delegate ownership/lookup
     * to the same TransactionLifecycleService, so either can serve any transfer transactionId.
     */
    @GetMapping("/{transactionId}/receipt")
    public ReceiptResponse receipt(@PathVariable UUID transactionId) {
        return internalTransferService.receipt(transactionId);
    }
}
