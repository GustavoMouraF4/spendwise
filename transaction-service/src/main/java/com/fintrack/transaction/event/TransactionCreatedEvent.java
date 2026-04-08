package com.fintrack.transaction.event;

import com.fintrack.transaction.entity.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionCreatedEvent(
        UUID transactionId,
        UUID userId,
        BigDecimal amount,
        Transaction.TransactionType type,
        String category,
        String description,
        LocalDate transactionDate,
        LocalDateTime occurredAt
) {
    public static TransactionCreatedEvent from(Transaction t) {
        return new TransactionCreatedEvent(
                t.getId(), t.getUserId(), t.getAmount(), t.getType(),
                t.getCategory(), t.getDescription(), t.getTransactionDate(),
                LocalDateTime.now()
        );
    }
}
