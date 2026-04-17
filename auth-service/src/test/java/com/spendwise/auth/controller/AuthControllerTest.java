package com.spendwise.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.auth.dto.response.AuthResponse;
import com.spendwise.auth.dto.response.UserDto;
import com.spendwise.auth.exception.AccountInactiveException;
import com.spendwise.auth.exception.EmailAlreadyExistsException;
import com.spendwise.auth.exception.GlobalExceptionHandler;
import com.spendwise.auth.exception.InvalidCredentialsException;
import com.spendwise.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AuthController.class, JwksController.class})
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AuthController — testes de slice Web")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AuthService authService;

    private static final String BASE_URL = "/api/v1/auth";

    private AuthResponse buildAuthResponse() {
        UserDto user = new UserDto(UUID.randomUUID(), "João Silva", "joao@email.com", "USER");
        return new AuthResponse("access-token", "refresh-token", "Bearer", 3600000L, user);
    }

    // ─── Register ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve retornar 201 quando registro for válido (CA-AUTH-01)")
    void shouldReturn201_whenRegisterIsValid() throws Exception {
        // Arrange
        when(authService.register(any())).thenReturn(buildAuthResponse());

        String body = """
                { "name": "João Silva", "email": "joao@email.com", "password": "senha123" }
                """;

        // Act + Assert
        mockMvc.perform(post(BASE_URL + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.role").value("USER"));
    }

    @Test
    @DisplayName("Deve retornar 409 quando e-mail já estiver cadastrado (CA-AUTH-02)")
    void shouldReturn409_whenEmailAlreadyExists() throws Exception {
        // Arrange
        when(authService.register(any())).thenThrow(new EmailAlreadyExistsException("joao@email.com"));

        String body = """
                { "name": "João Silva", "email": "joao@email.com", "password": "senha123" }
                """;

        // Act + Assert
        mockMvc.perform(post(BASE_URL + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Email já cadastrado"));
    }

    @Test
    @DisplayName("Deve retornar 400 quando senha tiver menos de 8 caracteres (CA-AUTH-03)")
    void shouldReturn400_whenPasswordTooShort() throws Exception {
        // Arrange
        String body = """
                { "name": "João Silva", "email": "joao@email.com", "password": "1234567" }
                """;

        // Act + Assert
        mockMvc.perform(post(BASE_URL + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    @DisplayName("Deve retornar 400 quando e-mail for inválido")
    void shouldReturn400_whenEmailIsInvalid() throws Exception {
        // Arrange
        String body = """
                { "name": "João Silva", "email": "email-invalido", "password": "senha123" }
                """;

        // Act + Assert
        mockMvc.perform(post(BASE_URL + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    // ─── Login ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve retornar 200 com tokens quando login for válido (CA-AUTH-06)")
    void shouldReturn200_whenLoginIsValid() throws Exception {
        // Arrange
        when(authService.login(any())).thenReturn(buildAuthResponse());

        String body = """
                { "email": "joao@email.com", "password": "senha123" }
                """;

        // Act + Assert
        mockMvc.perform(post(BASE_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }

    @Test
    @DisplayName("Deve retornar 401 com mensagem genérica quando credenciais forem inválidas (CA-AUTH-07)")
    void shouldReturn401_whenCredentialsAreInvalid() throws Exception {
        // Arrange
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        String body = """
                { "email": "joao@email.com", "password": "senhaerrada" }
                """;

        // Act + Assert
        mockMvc.perform(post(BASE_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Credenciais inválidas"));
    }

    @Test
    @DisplayName("Deve retornar 401 quando conta estiver inativa (CA-AUTH-08)")
    void shouldReturn401_whenAccountIsInactive() throws Exception {
        // Arrange
        when(authService.login(any())).thenThrow(new AccountInactiveException());

        String body = """
                { "email": "joao@email.com", "password": "senha123" }
                """;

        // Act + Assert
        mockMvc.perform(post(BASE_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Conta desativada"));
    }

    // ─── Logout ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve retornar 204 quando logout for bem-sucedido (CA-AUTH-09)")
    void shouldReturn204_whenLogoutIsSuccessful() throws Exception {
        // Arrange
        doNothing().when(authService).logout(any());

        // Act + Assert
        mockMvc.perform(post(BASE_URL + "/logout")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isNoContent());

        verify(authService).logout("Bearer valid-token");
    }

    // ─── Refresh ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve retornar 200 com novos tokens quando refresh for válido (CA-AUTH-12)")
    void shouldReturn200_whenRefreshIsValid() throws Exception {
        // Arrange
        when(authService.refresh("my-refresh-token")).thenReturn(buildAuthResponse());

        // Act + Assert
        mockMvc.perform(post(BASE_URL + "/refresh")
                        .header("X-Refresh-Token", "my-refresh-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }
}
