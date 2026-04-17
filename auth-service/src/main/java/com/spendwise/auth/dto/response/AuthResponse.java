package com.spendwise.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resposta de autenticação com tokens JWT")
public record AuthResponse(

        @Schema(description = "Token de acesso JWT (válido por 1 hora)")
        String accessToken,

        @Schema(description = "Token de renovação JWT (válido por 7 dias)")
        String refreshToken,

        @Schema(description = "Tipo do token", example = "Bearer")
        String tokenType,

        @Schema(description = "Expiração do access token em milissegundos", example = "3600000")
        long expiresIn,

        @Schema(description = "Dados do usuário autenticado")
        UserDto user
) {}
