package com.spendwise.transaction.controller;

import com.spendwise.transaction.dto.request.CreateTransactionRequest;
import com.spendwise.transaction.dto.request.UpdateTransactionRequest;
import com.spendwise.transaction.dto.response.TransactionResponse;
import com.spendwise.transaction.dto.response.TransactionSummaryResponse;
import com.spendwise.transaction.enums.TransactionType;
import com.spendwise.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions", description = "Lançamentos financeiros — receitas, gastos e transferências (RN-TRX-01 a RN-TRX-06)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    @Operation(summary = "Criar transação",
               description = "Registra um lançamento financeiro e publica evento transaction.created no Kafka (CA-TRX-01).")
    @ApiResponse(responseCode = "201", description = "Transação criada", content = @Content(schema = @Schema(implementation = TransactionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Conta não encontrada", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody CreateTransactionRequest request,
                                                      JwtAuthenticationToken auth) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.create(request, userId));
    }

    @GetMapping
    @Operation(summary = "Listar transações",
               description = "Lista paginada das transações do usuário. Filtre por type=INCOME ou type=EXPENSE (CA-TRX-07 a CA-TRX-09).")
    @ApiResponse(responseCode = "200", description = "Página de transações")
    public ResponseEntity<Page<TransactionResponse>> list(
            @RequestParam(required = false) TransactionType type,
            @PageableDefault(size = 20, sort = "transactionDate") Pageable pageable,
            JwtAuthenticationToken auth) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        return ResponseEntity.ok(transactionService.list(userId, type, pageable));
    }

    @GetMapping("/summary")
    @Operation(summary = "Resumo financeiro (dashboard)",
               description = "Retorna totalIncome, totalExpense e balance para o dashboard do usuário.")
    @ApiResponse(responseCode = "200", description = "Resumo calculado", content = @Content(schema = @Schema(implementation = TransactionSummaryResponse.class)))
    public ResponseEntity<TransactionSummaryResponse> summary(JwtAuthenticationToken auth) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        return ResponseEntity.ok(transactionService.summary(userId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar transação por ID")
    @ApiResponse(responseCode = "200", description = "Transação encontrada", content = @Content(schema = @Schema(implementation = TransactionResponse.class)))
    @ApiResponse(responseCode = "404", description = "Não encontrada ou pertence a outro usuário", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<TransactionResponse> findById(@PathVariable UUID id, JwtAuthenticationToken auth) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        return ResponseEntity.ok(transactionService.findById(id, userId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar transação (CA-TRX-10)")
    @ApiResponse(responseCode = "200", description = "Transação atualizada", content = @Content(schema = @Schema(implementation = TransactionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Não encontrada ou pertence a outro usuário", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<TransactionResponse> update(@PathVariable UUID id,
                                                      @Valid @RequestBody UpdateTransactionRequest request,
                                                      JwtAuthenticationToken auth) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        return ResponseEntity.ok(transactionService.update(id, request, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir transação (CA-TRX-11, RN-TRX-05)")
    @ApiResponse(responseCode = "204", description = "Transação removida")
    @ApiResponse(responseCode = "404", description = "Não encontrada ou pertence a outro usuário", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> delete(@PathVariable UUID id, JwtAuthenticationToken auth) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        transactionService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }
}
