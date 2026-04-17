package com.spendwise.auth.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Auth Service — testes de integração ponta-a-ponta")
class AuthFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine").withReuse(true);

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379)
                    .withReuse(true);

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private static final String BASE = "/api/v1/auth";

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@email.com";
    }

    private String registerAndGetAccessToken(String email) throws Exception {
        String body = """
                { "name": "Test User", "email": "%s", "password": "senha123" }
                """.formatted(email);

        MvcResult result = mockMvc.perform(post(BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        Map<?, ?> response = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (String) response.get("accessToken");
    }

    private String registerAndGetRefreshToken(String email) throws Exception {
        String body = """
                { "name": "Test User", "email": "%s", "password": "senha123" }
                """.formatted(email);

        MvcResult result = mockMvc.perform(post(BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        Map<?, ?> response = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (String) response.get("refreshToken");
    }

    // ─── Register ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve registrar usuário e retornar tokens JWT (CA-AUTH-01, CA-AUTH-05)")
    void shouldRegisterAndReturnTokens() throws Exception {
        // Arrange
        String email = uniqueEmail();
        String body = """
                { "name": "João Silva", "email": "%s", "password": "senha123" }
                """.formatted(email);

        // Act + Assert
        mockMvc.perform(post(BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.email").value(email));
    }

    @Test
    @DisplayName("Deve retornar 409 ao registrar e-mail duplicado (CA-AUTH-02)")
    void shouldReturn409_whenRegisteringDuplicateEmail() throws Exception {
        // Arrange
        String email = uniqueEmail();
        String body = """
                { "name": "João Silva", "email": "%s", "password": "senha123" }
                """.formatted(email);

        mockMvc.perform(post(BASE + "/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        // Act + Assert — segundo cadastro com mesmo e-mail
        mockMvc.perform(post(BASE + "/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Email já cadastrado"));
    }

    @Test
    @DisplayName("Deve retornar 400 quando senha tiver menos de 8 caracteres (CA-AUTH-03)")
    void shouldReturn400_whenPasswordTooShort() throws Exception {
        // Arrange
        String body = """
                { "name": "João Silva", "email": "%s", "password": "abc123" }
                """.formatted(uniqueEmail());

        // Act + Assert
        mockMvc.perform(post(BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    // ─── Login ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve fazer login com usuário registrado (CA-AUTH-06)")
    void shouldLoginWithRegisteredUser() throws Exception {
        // Arrange
        String email = uniqueEmail();
        registerAndGetAccessToken(email);

        String loginBody = """
                { "email": "%s", "password": "senha123" }
                """.formatted(email);

        // Act + Assert
        mockMvc.perform(post(BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email));
    }

    @Test
    @DisplayName("Deve retornar 401 com mensagem genérica quando senha for errada (CA-AUTH-07, RN-AUTH-06)")
    void shouldReturn401_whenLoginWithWrongPassword() throws Exception {
        // Arrange
        String email = uniqueEmail();
        registerAndGetAccessToken(email);

        String loginBody = """
                { "email": "%s", "password": "senhaerrada" }
                """.formatted(email);

        // Act + Assert
        MvcResult result = mockMvc.perform(post(BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isUnauthorized())
                .andReturn();

        // A mensagem não deve revelar existência do e-mail (RN-AUTH-06)
        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).contains("Credenciais inválidas");
        assertThat(responseBody).doesNotContain("email");
    }

    // ─── Logout ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve invalidar token após logout (CA-AUTH-09, CA-AUTH-10, RN-AUTH-10)")
    void shouldLogoutAndBlacklistToken() throws Exception {
        // Arrange
        String email = uniqueEmail();
        String accessToken = registerAndGetAccessToken(email);

        // Act — logout
        mockMvc.perform(post(BASE + "/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        // Assert — mesmo token é rejeitado em chamada subsequente
        mockMvc.perform(post(BASE + "/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    // ─── Refresh ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve renovar tokens com rotação (CA-AUTH-12, CA-AUTH-14)")
    void shouldRefreshAndRotateTokens() throws Exception {
        // Arrange
        String email = uniqueEmail();
        String refreshToken = registerAndGetRefreshToken(email);

        // Act
        MvcResult result = mockMvc.perform(post(BASE + "/refresh")
                        .header("X-Refresh-Token", refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        // Assert — novos tokens são diferentes dos originais
        Map<?, ?> response = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertThat(response.get("refreshToken")).isNotEqualTo(refreshToken);
    }

    // ─── JWKS ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve expor chave pública RSA no endpoint JWKS (RN-SEC-02)")
    void shouldExposeJwks() throws Exception {
        // Act + Assert
        mockMvc.perform(get(BASE + "/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys").isArray())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andExpect(jsonPath("$.keys[0].n").isNotEmpty())
                .andExpect(jsonPath("$.keys[0].e").isNotEmpty());
    }
}
