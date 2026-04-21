package com.spendwise.transaction.dto.request;

import com.spendwise.transaction.enums.OperationType;
import com.spendwise.transaction.enums.RecurrenceType;
import com.spendwise.transaction.enums.TransactionType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateTransactionRequest(
        @NotNull @Positive
        BigDecimal amount,

        @NotNull
        TransactionType type,

        @NotNull
        OperationType operationType,

        @NotNull
        UUID accountId,

        @NotBlank @Size(max = 100)
        String category,

        @NotBlank @Size(max = 255)
        String description,

        @NotNull @PastOrPresent
        LocalDate transactionDate,

        @Size(max = 100)
        String tags,

        @NotNull
        Boolean recurring,

        RecurrenceType recurrenceType
) {}
