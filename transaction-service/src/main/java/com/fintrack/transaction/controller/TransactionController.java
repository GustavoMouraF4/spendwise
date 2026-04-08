package com.fintrack.transaction.controller;

import com.fintrack.transaction.dto.request.TransactionRequest;
import com.fintrack.transaction.dto.response.TransactionResponse;
import com.fintrack.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Gerenciamento de transações financeiras")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar nova transação")
    public TransactionResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TransactionRequest request) {
        return transactionService.create(UUID.fromString(jwt.getSubject()), request);
    }

    @GetMapping
    @Operation(summary = "Listar transações paginadas")
    public Page<TransactionResponse> findAll(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20, sort = "transactionDate") Pageable pageable) {
        return transactionService.findAll(UUID.fromString(jwt.getSubject()), pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar transação por ID")
    public TransactionResponse findById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        return transactionService.findById(UUID.fromString(jwt.getSubject()), id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar transação")
    public TransactionResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody TransactionRequest request) {
        return transactionService.update(UUID.fromString(jwt.getSubject()), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deletar transação")
    public void delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        transactionService.delete(UUID.fromString(jwt.getSubject()), id);
    }
}
