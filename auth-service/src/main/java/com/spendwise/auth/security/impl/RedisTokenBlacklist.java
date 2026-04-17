package com.spendwise.auth.security.impl;

import com.spendwise.auth.security.port.TokenBlacklist;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisTokenBlacklist implements TokenBlacklist {

    private static final String KEY_PREFIX = "blacklist:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void addToBlacklist(String jti, Duration ttl) {
        if (ttl.isPositive()) {
            redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
        }
    }

    @Override
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
    }
}
