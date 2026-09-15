package com.minibank.transaction.entity;

public enum TransactionStep {
    INITIATED,
    CONFIRMED,
    EXECUTED,
    SETTLED,
    FAILED,
    EXPIRED
}
