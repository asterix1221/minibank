package com.minibank.payment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "payment_templates")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentTemplate {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "recipient_details", nullable = false, length = 500)
    private String recipientDetails;

    @Column(name = "default_amount", precision = 19, scale = 2)
    private BigDecimal defaultAmount;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    public static PaymentTemplate create(UUID clientId, String name, String recipientDetails,
                                          BigDecimal defaultAmount, String category) {
        PaymentTemplate template = new PaymentTemplate();
        template.clientId = clientId;
        template.name = name;
        template.recipientDetails = recipientDetails;
        template.defaultAmount = defaultAmount;
        template.category = category;
        return template;
    }
}
