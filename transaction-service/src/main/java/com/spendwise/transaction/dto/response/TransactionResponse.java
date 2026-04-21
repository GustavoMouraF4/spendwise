package com.spendwise.transaction.dto.response;

import com.spendwise.transaction.enums.OperationType;
import com.spendwise.transaction.enums.RecurrenceType;
import com.spendwise.transaction.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID accountId,
        String accountName,
        BigDecimal amount,
        TransactionType type,
        OperationType operationType,
        String category,
        String description,
        LocalDate transactionDate,
        String tags,
        boolean recurring,
        RecurrenceType recurrenceType,
        Instant createdAt,
        Instant updatedAt
) {}
