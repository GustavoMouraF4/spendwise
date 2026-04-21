package com.spendwise.transaction.dto.response;

import com.spendwise.transaction.enums.OperationType;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        Set<OperationType> availableOperationTypes,
        Instant createdAt
) {}
