package com.fintrack.transaction.dto.response;

import com.fintrack.transaction.entity.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        BigDecimal amount,
        Transaction.TransactionType type,
        String category,
        String description,
        LocalDate transactionDate,
        String tags,
        boolean recurring,
        Transaction.RecurrenceType recurrenceType,
        LocalDateTime createdAt
) {
    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
                t.getId(), t.getAmount(), t.getType(),
                t.getCategory(), t.getDescription(), t.getTransactionDate(),
                t.getTags(), t.isRecurring(), t.getRecurrenceType(), t.getCreatedAt()
        );
    }
}
