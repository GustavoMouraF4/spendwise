package com.spendwise.transaction.controller;

import com.spendwise.transaction.dto.request.CreateAccountRequest;
import com.spendwise.transaction.dto.response.AccountResponse;
import com.spendwise.transaction.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts", description = "Contas pré-registradas do usuário (placeholder para Open Finance)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    @Operation(summary = "Criar conta", description = "Registra uma nova conta com os tipos de operação disponíveis.")
    @ApiResponse(responseCode = "201", description = "Conta criada", content = @Content(schema = @Schema(implementation = AccountResponse.class)))
    @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request,
                                                  JwtAuthenticationToken auth) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.create(request, userId));
    }

    @GetMapping
    @Operation(summary = "Listar contas", description = "Retorna todas as contas do usuário autenticado.")
    @ApiResponse(responseCode = "200", description = "Lista de contas")
    public ResponseEntity<List<AccountResponse>> list(JwtAuthenticationToken auth) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        return ResponseEntity.ok(accountService.listByUser(userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remover conta", description = "Remove uma conta do usuário. Retorna 404 se não pertencer ao usuário.")
    @ApiResponse(responseCode = "204", description = "Conta removida")
    @ApiResponse(responseCode = "404", description = "Conta não encontrada", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> delete(@PathVariable UUID id, JwtAuthenticationToken auth) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        accountService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }
}
