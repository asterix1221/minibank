package com.minibank.account.repository;

import com.minibank.account.entity.Card;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CardRepository extends JpaRepository<Card, UUID> {

    boolean existsByCardNumber(String cardNumber);

    List<Card> findByAccountId(UUID accountId);
}
