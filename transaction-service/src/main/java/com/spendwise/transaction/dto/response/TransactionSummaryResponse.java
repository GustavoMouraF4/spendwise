package com.spendwise.transaction.dto.response;

import java.math.BigDecimal;

public record TransactionSummaryResponse(
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal balance
) {}
