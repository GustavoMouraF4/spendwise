package com.spendwise.auth.security.impl;

import com.spendwise.auth.config.JwtProperties;
import com.spendwise.auth.entity.User;
import com.spendwise.auth.fixtures.UserFixtures;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtService — testes unitários")
class JwtServiceTest {

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() throws Exception {
        JwtProperties properties = new JwtProperties();
        properties.setAccessTokenExpiration(3600000L);
        properties.setRefreshTokenExpiration(604800000L);

        jwtService = new JwtService(properties);
        jwtService.init();

        user = UserFixtures.anActiveUser();
    }

    // ─── Access Token ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve gerar access token com claims corretas")
    void shouldGenerateAccessToken_withCorrectClaims() {
        // Act
        String token = jwtService.generateAccessToken(user);
        Claims claims = jwtService.parseToken(token);

        // Assert
        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get("email", String.class)).isEqualTo(user.getEmail());
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getExpiration()).isNotNull();
    }

    // ─── Refresh Token ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve gerar refresh token com claim type=refresh")
    void shouldGenerateRefreshToken_withTypeRefresh() {
        // Act
        String token = jwtService.generateRefreshToken(user);
        Claims claims = jwtService.parseToken(token);

        // Assert
        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get("type", String.class)).isEqualTo("refresh");
        assertThat(claims.getId()).isNotBlank();
    }

    // ─── Parse ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve fazer parse de token válido sem lançar exceção")
    void shouldParseToken_whenTokenIsValid() {
        // Arrange
        String token = jwtService.generateAccessToken(user);

        // Act
        Claims claims = jwtService.parseToken(token);

        // Assert
        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
    }

    @Test
    @DisplayName("Deve lançar JwtException quando token for adulterado")
    void shouldThrowJwtException_whenTokenIsTampered() {
        // Arrange
        String token = jwtService.generateAccessToken(user);
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "invalidsignature";

        // Act + Assert
        assertThatThrownBy(() -> jwtService.parseToken(tampered))
                .isInstanceOf(JwtException.class);
    }

    // ─── TTL ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve retornar TTL positivo para token recém-gerado")
    void shouldReturnPositiveTtl_whenTokenIsNotExpired() {
        // Arrange
        String token = jwtService.generateAccessToken(user);
        Claims claims = jwtService.parseToken(token);

        // Act
        Duration ttl = jwtService.getRemainingTtl(claims);

        // Assert
        assertThat(ttl).isPositive();
        assertThat(ttl.toMinutes()).isGreaterThan(50);
    }

    // ─── isRefreshToken ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve detectar corretamente que o token é do tipo refresh")
    void shouldReturnTrue_whenTokenIsRefreshType() {
        // Arrange
        String refreshToken = jwtService.generateRefreshToken(user);
        Claims claims = jwtService.parseToken(refreshToken);

        // Act + Assert
        assertThat(jwtService.isRefreshToken(claims)).isTrue();
    }

    @Test
    @DisplayName("Deve retornar false para access token verificado como refresh")
    void shouldReturnFalse_whenTokenIsAccessType() {
        // Arrange
        String accessToken = jwtService.generateAccessToken(user);
        Claims claims = jwtService.parseToken(accessToken);

        // Act + Assert
        assertThat(jwtService.isRefreshToken(claims)).isFalse();
    }

    // ─── JWKS ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve expor chave pública RSA com todos os campos JWK obrigatórios")
    void shouldExposePublicKeyAsJwk_withRequiredFields() {
        // Act
        Map<String, Object> jwk = jwtService.getPublicKeyAsJwk();

        // Assert
        assertThat(jwk)
                .containsKey("kty")
                .containsKey("use")
                .containsKey("alg")
                .containsKey("kid")
                .containsKey("n")
                .containsKey("e");
        assertThat(jwk.get("kty")).isEqualTo("RSA");
        assertThat(jwk.get("use")).isEqualTo("sig");
        assertThat(jwk.get("alg")).isEqualTo("RS256");
    }
}
