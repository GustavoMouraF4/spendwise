package com.fintrack.auth.service;

import com.fintrack.auth.dto.request.LoginRequest;
import com.fintrack.auth.dto.request.RegisterRequest;
import com.fintrack.auth.dto.response.AuthResponse;
import com.fintrack.auth.dto.response.UserResponse;
import com.fintrack.auth.entity.User;
import com.fintrack.auth.exception.EmailAlreadyExistsException;
import com.fintrack.auth.exception.InvalidCredentialsException;
import com.fintrack.auth.repository.UserRepository;
import com.fintrack.auth.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException("Email já cadastrado: " + request.email());
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .build();

        userRepository.save(user);
        log.info("Novo usuário registrado: {}", user.getEmail());

        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Credenciais inválidas"));

        if (!user.isActive()) {
            throw new InvalidCredentialsException("Conta desativada");
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException("Credenciais inválidas");
        }

        log.info("Login realizado: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    public void logout(String accessToken) {
        if (jwtService.isTokenValid(accessToken)) {
            String userId = jwtService.extractUserId(accessToken);
            redisTemplate.opsForValue().set(
                "blacklist:" + accessToken,
                userId,
                Duration.ofMillis(jwtService.getAccessTokenExpiration())
            );
            log.info("Logout efetuado para usuário: {}", userId);
        }
    }

    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtService.isTokenValid(refreshToken)) {
            throw new InvalidCredentialsException("Refresh token inválido ou expirado");
        }

        String userId = jwtService.extractUserId(refreshToken);
        User user = userRepository.findById(java.util.UUID.fromString(userId))
                .orElseThrow(() -> new InvalidCredentialsException("Usuário não encontrado"));

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(
                user.getId().toString(), user.getEmail(), user.getRole().name()
        );
        String refreshToken = jwtService.generateRefreshToken(user.getId().toString());

        return AuthResponse.of(
                accessToken,
                refreshToken,
                jwtService.getAccessTokenExpiration(),
                UserResponse.from(user)
        );
    }
}
