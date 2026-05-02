package com.travel.travelplanner.auth.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.travel.travelplanner.auth.api.AuthResponse;
import com.travel.travelplanner.auth.domain.AuthToken;
import com.travel.travelplanner.auth.domain.User;
import com.travel.travelplanner.auth.repository.AuthTokenRepository;
import com.travel.travelplanner.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final Duration TOKEN_TTL = Duration.ofDays(30);

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom rng = new SecureRandom();

    public AuthResponse signup(String email, String password) {
        String normEmail = normalizeEmail(email);
        userRepository.findByEmail(normEmail).ifPresent(u -> {
            throw new IllegalArgumentException("Email already registered");
        });

        User created = userRepository.save(User.builder()
                .email(normEmail)
                .passwordHash(encoder.encode(password))
                .createdAt(Instant.now())
                .build());

        AuthToken token = issueToken(created.getId());
        return new AuthResponse(token.getToken(), created.getId(), created.getEmail());
    }

    public AuthResponse signin(String email, String password) {
        String normEmail = normalizeEmail(email);
        User user = userRepository.findByEmail(normEmail)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!encoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        AuthToken token = issueToken(user.getId());
        return new AuthResponse(token.getToken(), user.getId(), user.getEmail());
    }

    public String authenticateToken(String token) {
        if (token == null || token.isBlank()) return null;
        return authTokenRepository.findByToken(token.trim())
                .filter(t -> t.getExpiresAt() == null || t.getExpiresAt().isAfter(Instant.now()))
                .map(AuthToken::getUserId)
                .orElse(null);
    }

    private AuthToken issueToken(String userId) {
        String token = generateToken();
        Instant now = Instant.now();
        AuthToken saved = authTokenRepository.save(AuthToken.builder()
                .token(token)
                .userId(userId)
                .createdAt(now)
                .expiresAt(now.plus(TOKEN_TTL))
                .build());
        return saved;
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        rng.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalizeEmail(String email) {
        if (email == null) throw new IllegalArgumentException("Email is required");
        return email.trim().toLowerCase();
    }
}

