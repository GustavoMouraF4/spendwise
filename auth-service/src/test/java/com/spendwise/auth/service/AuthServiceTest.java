package com.spendwise.auth.service;

import com.spendwise.auth.config.JwtProperties;
import com.spendwise.auth.dto.request.LoginRequest;
import com.spendwise.auth.dto.request.RegisterRequest;
import com.spendwise.auth.dto.response.AuthResponse;
import com.spendwise.auth.entity.User;
import com.spendwise.auth.exception.AccountInactiveException;
import com.spendwise.auth.exception.EmailAlreadyExistsException;
import com.spendwise.auth.exception.InvalidCredentialsException;
import com.spendwise.auth.exception.InvalidTokenException;
import com.spendwise.auth.fixtures.UserFixtures;
import com.spendwise.auth.repository.UserRepository;
import com.spendwise.auth.security.impl.JwtService;
import com.spendwise.auth.security.port.JwtPort;
import com.spendwise.auth.security.port.TokenBlacklist;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService — testes unitários")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtPort jwtPort;
    @Mock private JwtService jwtService;
    @Mock private TokenBlacklist tokenBlacklist;
    @Mock private JwtProperties jwtProperties;

    @InjectMocks
    private AuthService authService;

    // ─── Register ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve registrar usuário quando o e-mail for único (CA-AUTH-01)")
    void shouldRegister_whenEmailIsUnique() {
        // Arrange
        RegisterRequest request = new RegisterRequest("João Silva", "joao@email.com", "senha123");
        User saved = UserFixtures.anActiveUser();

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("$2a$10$hash");
        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(jwtPort.generateAccessToken(any())).thenReturn("access-token");
        when(jwtPort.generateRefreshToken(any())).thenReturn("refresh-token");
        when(jwtProperties.getAccessTokenExpiration()).thenReturn(3600000L);

        // Act
        AuthResponse response = authService.register(request);

        // Assert
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().role()).isEqualTo("USER");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Deve lançar EmailAlreadyExistsException quando e-mail duplicado (CA-AUTH-02)")
    void shouldThrowEmailAlreadyExists_whenEmailIsDuplicate() {
        // Arrange
        RegisterRequest request = new RegisterRequest("João Silva", "joao@email.com", "senha123");
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    // ─── Login ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve realizar login quando credenciais são válidas (CA-AUTH-06)")
    void shouldLogin_whenCredentialsAreValid() {
        // Arrange
        LoginRequest request = new LoginRequest("joao@email.com", "senha123");
        User user = UserFixtures.anActiveUser();

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(true);
        when(jwtPort.generateAccessToken(user)).thenReturn("access-token");
        when(jwtPort.generateRefreshToken(user)).thenReturn("refresh-token");
        when(jwtProperties.getAccessTokenExpiration()).thenReturn(3600000L);

        // Act
        AuthResponse response = authService.login(request);

        // Assert
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.user().email()).isEqualTo(user.getEmail());
    }

    @Test
    @DisplayName("Deve lançar InvalidCredentialsException quando e-mail não existe (RN-AUTH-06)")
    void shouldThrowInvalidCredentials_whenEmailNotFound() {
        // Arrange
        LoginRequest request = new LoginRequest("naoexiste@email.com", "senha123");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Credenciais inválidas");
    }

    @Test
    @DisplayName("Deve lançar InvalidCredentialsException com mensagem genérica quando senha errada (RN-AUTH-06)")
    void shouldThrowInvalidCredentials_whenPasswordWrong() {
        // Arrange
        LoginRequest request = new LoginRequest("joao@email.com", "senhaerrada");
        User user = UserFixtures.anActiveUser();

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(false);

        // Act + Assert
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Credenciais inválidas");
    }

    @Test
    @DisplayName("Deve lançar AccountInactiveException quando conta estiver desativada (CA-AUTH-08)")
    void shouldThrowAccountInactive_whenUserIsInactive() {
        // Arrange
        LoginRequest request = new LoginRequest("joao@email.com", "senha123");
        User user = UserFixtures.anInactiveUser();

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AccountInactiveException.class)
                .hasMessage("Conta desativada");
    }

    // ─── Logout ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve adicionar token à blacklist no logout (CA-AUTH-09)")
    void shouldLogout_whenTokenIsValid() {
        // Arrange
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn("jti-123");
        when(jwtPort.parseToken(anyString())).thenReturn(claims);
        when(jwtPort.getRemainingTtl(claims)).thenReturn(Duration.ofMinutes(30));

        // Act
        authService.logout("Bearer valid-token");

        // Assert
        verify(tokenBlacklist).addToBlacklist("jti-123", Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("Deve lançar InvalidTokenException quando token de logout for inválido")
    void shouldThrowInvalidToken_whenLogoutWithExpiredToken() {
        // Arrange
        when(jwtPort.parseToken(anyString())).thenThrow(new JwtException("expired"));

        // Act + Assert
        assertThatThrownBy(() -> authService.logout("Bearer expired-token"))
                .isInstanceOf(InvalidTokenException.class);

        verifyNoInteractions(tokenBlacklist);
    }

    // ─── Refresh ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve renovar tokens quando refresh token for válido (CA-AUTH-12)")
    void shouldRefresh_whenRefreshTokenIsValid() {
        // Arrange
        User user = UserFixtures.anActiveUser();
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(user.getId().toString());
        when(jwtPort.parseToken("refresh-token")).thenReturn(claims);
        when(jwtService.isRefreshToken(claims)).thenReturn(true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(jwtPort.generateAccessToken(user)).thenReturn("new-access");
        when(jwtPort.generateRefreshToken(user)).thenReturn("new-refresh");
        when(jwtProperties.getAccessTokenExpiration()).thenReturn(3600000L);

        // Act
        AuthResponse response = authService.refresh("refresh-token");

        // Assert
        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
    }

    @Test
    @DisplayName("Deve lançar InvalidTokenException quando token não for do tipo refresh (CA-AUTH-13)")
    void shouldThrowInvalidToken_whenNotARefreshToken() {
        // Arrange
        Claims claims = mock(Claims.class);
        when(jwtPort.parseToken("access-token")).thenReturn(claims);
        when(jwtService.isRefreshToken(claims)).thenReturn(false);

        // Act + Assert
        assertThatThrownBy(() -> authService.refresh("access-token"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("refresh token");
    }

    @Test
    @DisplayName("Deve lançar InvalidTokenException quando refresh token for inválido (CA-AUTH-13)")
    void shouldThrowInvalidToken_whenRefreshTokenIsExpired() {
        // Arrange
        when(jwtPort.parseToken("expired-refresh")).thenThrow(new JwtException("expired"));

        // Act + Assert
        assertThatThrownBy(() -> authService.refresh("expired-refresh"))
                .isInstanceOf(InvalidTokenException.class);
    }
}
