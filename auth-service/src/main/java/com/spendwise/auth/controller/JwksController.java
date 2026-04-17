package com.spendwise.auth.controller;

import com.spendwise.auth.security.port.JwtPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Autenticação e autorização — emissão e renovação de tokens JWT")
@RequiredArgsConstructor
public class JwksController {

    private final JwtPort jwtPort;

    @GetMapping("/jwks")
    @Operation(summary = "JSON Web Key Set (JWKS)",
               description = "Expõe a chave pública RSA usada para assinar os tokens. " +
                             "Consumido pelos demais serviços para validação local (RN-SEC-02).")
    @ApiResponse(responseCode = "200", description = "JWKS com chave pública RSA")
    public ResponseEntity<Map<String, Object>> jwks() {
        Map<String, Object> jwks = Map.of("keys", List.of(jwtPort.getPublicKeyAsJwk()));
        return ResponseEntity.ok(jwks);
    }
}
