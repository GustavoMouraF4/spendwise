package com.spendwise.auth.controller;

import com.spendwise.auth.dto.request.LoginRequest;
import com.spendwise.auth.dto.request.RegisterRequest;
import com.spendwise.auth.dto.response.AuthResponse;
import com.spendwise.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Autenticação e autorização — emissão e renovação de tokens JWT")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Registro de usuário",
               description = "Cria novo usuário e retorna tokens JWT imediatamente (RN-AUTH-01 a RN-AUTH-05).")
    @ApiResponse(responseCode = "201", description = "Usuário criado com sucesso",
                 content = @Content(schema = @Schema(implementation = AuthResponse.class)))
    @ApiResponse(responseCode = "409", description = "E-mail já cadastrado",
                 content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "400", description = "Dados inválidos (nome, e-mail ou senha)",
                 content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Login",
               description = "Autentica usuário ativo e retorna tokens JWT (RN-AUTH-06 a RN-AUTH-09).")
    @ApiResponse(responseCode = "200", description = "Login bem-sucedido",
                 content = @Content(schema = @Schema(implementation = AuthResponse.class)))
    @ApiResponse(responseCode = "401", description = "Credenciais inválidas ou conta inativa",
                 content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout",
               description = "Invalida o access token via blacklist no Redis (RN-AUTH-10 a RN-AUTH-12).")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "204", description = "Logout realizado com sucesso")
    @ApiResponse(responseCode = "401", description = "Token ausente ou inválido",
                 content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        authService.logout(authorization);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovação de token",
               description = "Gera novos access token e refresh token com rotação (RN-AUTH-13, RN-AUTH-14).")
    @ApiResponse(responseCode = "200", description = "Novos tokens gerados",
                 content = @Content(schema = @Schema(implementation = AuthResponse.class)))
    @ApiResponse(responseCode = "401", description = "Refresh token inválido ou expirado",
                 content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<AuthResponse> refresh(
            @RequestHeader("X-Refresh-Token") String refreshToken) {
        return ResponseEntity.ok(authService.refresh(refreshToken));
    }
}
