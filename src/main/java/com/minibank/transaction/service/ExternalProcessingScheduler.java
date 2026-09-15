package com.minibank.transaction.service;

import com.minibank.transaction.entity.Transaction;
import com.minibank.transaction.entity.TransactionStatus;
import com.minibank.transaction.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class ExternalProcessingScheduler {

    private final TransactionRepository transactionRepository;
    private final ExternalProcessingService processingService;
    private final long minProcessingAgeSeconds;

    public ExternalProcessingScheduler(TransactionRepository transactionRepository,
                                        ExternalProcessingService processingService,
                                        @Value("${minibank.external-processing.min-processing-age-seconds}") long minProcessingAgeSeconds) {
        this.transactionRepository = transactionRepository;
        this.processingService = processingService;
        this.minProcessingAgeSeconds = minProcessingAgeSeconds;
    }

    @Scheduled(fixedDelayString = "${minibank.external-processing.fixed-delay-ms}")
    public void processPending() {
        Instant threshold = Instant.now().minusSeconds(minProcessingAgeSeconds);
        List<Transaction> due = transactionRepository.findByStatusAndUpdatedAtBefore(TransactionStatus.PROCESSING, threshold);
        for (Transaction tx : due) {
            processingService.processOne(tx.getId());
        }
    }
}
