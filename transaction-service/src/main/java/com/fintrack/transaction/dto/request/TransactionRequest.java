package com.fintrack.transaction.dto.request;

import com.fintrack.transaction.entity.Transaction;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequest(
        @NotNull(message = "Valor é obrigatório")
        @Positive(message = "Valor deve ser positivo")
        @Digits(integer = 13, fraction = 2)
        BigDecimal amount,

        @NotNull(message = "Tipo é obrigatório")
        Transaction.TransactionType type,

        @NotBlank(message = "Categoria é obrigatória")
        @Size(max = 100)
        String category,

        @NotBlank(message = "Descrição é obrigatória")
        @Size(max = 255)
        String description,

        @NotNull(message = "Data é obrigatória")
        LocalDate transactionDate,

        String tags,

        boolean recurring,

        Transaction.RecurrenceType recurrenceType
) {}
