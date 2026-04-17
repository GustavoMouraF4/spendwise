package com.spendwise.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Dados públicos do usuário autenticado")
public record UserDto(

        @Schema(description = "Identificador único")
        UUID id,

        @Schema(description = "Nome completo")
        String name,

        @Schema(description = "E-mail")
        String email,

        @Schema(description = "Perfil de acesso")
        String role
) {}
