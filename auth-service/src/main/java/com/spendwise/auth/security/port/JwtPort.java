package com.spendwise.auth.security.port;

import com.spendwise.auth.entity.User;
import io.jsonwebtoken.Claims;

import java.time.Duration;
import java.util.Map;

public interface JwtPort {

    String generateAccessToken(User user);

    String generateRefreshToken(User user);

    Claims parseToken(String token);

    Duration getRemainingTtl(Claims claims);

    Map<String, Object> getPublicKeyAsJwk();
}
