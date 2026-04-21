package com.spendwise.transaction.dto.request;

import com.spendwise.transaction.enums.OperationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateAccountRequest(
        @NotBlank @Size(max = 100)
        String name,

        @NotEmpty
        Set<OperationType> availableOperationTypes
) {}
