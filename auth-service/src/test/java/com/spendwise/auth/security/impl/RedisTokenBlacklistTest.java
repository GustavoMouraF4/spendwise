package com.spendwise.auth.security.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisTokenBlacklist — testes unitários")
class RedisTokenBlacklistTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private RedisTokenBlacklist blacklist;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        blacklist = new RedisTokenBlacklist(redisTemplate);
    }

    @Test
    @DisplayName("Deve adicionar token à blacklist quando TTL for positivo (RN-AUTH-12)")
    void shouldAddToBlacklist_whenTtlIsPositive() {
        // Arrange
        String jti = "jti-abc-123";
        Duration ttl = Duration.ofMinutes(30);

        // Act
        blacklist.addToBlacklist(jti, ttl);

        // Assert
        verify(valueOperations).set("blacklist:" + jti, "1", ttl);
    }

    @Test
    @DisplayName("Deve ignorar inserção quando TTL for zero ou negativo")
    void shouldNotAddToBlacklist_whenTtlIsZeroOrNegative() {
        // Act
        blacklist.addToBlacklist("jti-zero", Duration.ZERO);
        blacklist.addToBlacklist("jti-neg", Duration.ofSeconds(-1));

        // Assert
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("Deve retornar true quando JTI estiver na blacklist (RN-AUTH-11)")
    void shouldReturnTrue_whenJtiIsBlacklisted() {
        // Arrange
        String jti = "jti-blacklisted";
        when(redisTemplate.hasKey("blacklist:" + jti)).thenReturn(Boolean.TRUE);

        // Act
        boolean result = blacklist.isBlacklisted(jti);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Deve retornar false quando JTI não estiver na blacklist")
    void shouldReturnFalse_whenJtiIsNotBlacklisted() {
        // Arrange
        String jti = "jti-valid";
        when(redisTemplate.hasKey("blacklist:" + jti)).thenReturn(Boolean.FALSE);

        // Act
        boolean result = blacklist.isBlacklisted(jti);

        // Assert
        assertThat(result).isFalse();
    }
}
