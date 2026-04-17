package com.spendwise.auth.service;

import com.spendwise.auth.config.JwtProperties;
import com.spendwise.auth.dto.request.LoginRequest;
import com.spendwise.auth.dto.request.RegisterRequest;
import com.spendwise.auth.dto.response.AuthResponse;
import com.spendwise.auth.dto.response.UserDto;
import com.spendwise.auth.entity.User;
import com.spendwise.auth.exception.AccountInactiveException;
import com.spendwise.auth.exception.EmailAlreadyExistsException;
import com.spendwise.auth.exception.InvalidCredentialsException;
import com.spendwise.auth.exception.InvalidTokenException;
import com.spendwise.auth.repository.UserRepository;
import com.spendwise.auth.security.impl.JwtService;
import com.spendwise.auth.security.port.JwtPort;
import com.spendwise.auth.security.port.TokenBlacklist;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String DEFAULT_ROLE = "USER";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtPort jwtPort;
    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;
    private final JwtProperties jwtProperties;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(DEFAULT_ROLE)
                .isActive(true)
                .build();

        userRepository.save(user);

        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        if (!user.isActive()) {
            throw new AccountInactiveException();
        }

        return buildAuthResponse(user);
    }

    public void logout(String bearerToken) {
        String token = extractRawToken(bearerToken);
        try {
            Claims claims = jwtPort.parseToken(token);
            tokenBlacklist.addToBlacklist(claims.getId(), jwtPort.getRemainingTtl(claims));
        } catch (JwtException e) {
            throw new InvalidTokenException("Token inválido ou expirado");
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtPort.parseToken(refreshToken);
        } catch (JwtException e) {
            throw new InvalidTokenException("Refresh token inválido ou expirado");
        }

        if (!jwtService.isRefreshToken(claims)) {
            throw new InvalidTokenException("Token fornecido não é um refresh token");
        }

        UUID userId = UUID.fromString(claims.getSubject());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("Usuário não encontrado"));

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtPort.generateAccessToken(user);
        String refreshToken = jwtPort.generateRefreshToken(user);

        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtProperties.getAccessTokenExpiration(),
                new UserDto(user.getId(), user.getName(), user.getEmail(), user.getRole())
        );
    }

    private String extractRawToken(String bearerToken) {
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return bearerToken;
    }
}
