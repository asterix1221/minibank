package com.minibank.transaction.controller;

import com.minibank.transaction.dto.TransactionEventDto;
import com.minibank.transaction.entity.Transaction;
import com.minibank.transaction.repository.TransactionEventRepository;
import com.minibank.transaction.service.TransactionLifecycleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionLifecycleService lifecycle;
    private final TransactionEventRepository eventRepository;

    public TransactionController(TransactionLifecycleService lifecycle, TransactionEventRepository eventRepository) {
        this.lifecycle = lifecycle;
        this.eventRepository = eventRepository;
    }

    @GetMapping("/{transactionId}/events")
    public List<TransactionEventDto> events(@PathVariable UUID transactionId) {
        Transaction tx = lifecycle.getOwnedOrThrow(transactionId);
        return eventRepository.findByTransactionIdOrderByCreatedAtAsc(tx.getId()).stream()
                .map(e -> new TransactionEventDto(e.getStep(), e.getStatus(), e.getPayload(), e.getCreatedAt()))
                .toList();
    }
}
