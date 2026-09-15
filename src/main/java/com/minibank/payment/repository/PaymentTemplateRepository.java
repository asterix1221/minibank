package com.minibank.payment.repository;

import com.minibank.payment.entity.PaymentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentTemplateRepository extends JpaRepository<PaymentTemplate, UUID> {

    List<PaymentTemplate> findByClientId(UUID clientId);
}
