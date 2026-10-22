package com.titran.pingcortex.service;

import com.titran.pingcortex.dto.request.RefreshToken;
import com.titran.pingcortex.exception.InvalidRefreshTokenException;
import com.titran.pingcortex.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration refreshTokenValidity;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final Duration ROTATION_GRACE_PERIOD = Duration.ofSeconds(30);

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${jwt.refresh-token-expiration-days}") long refreshTokenExpirationDays
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenValidity = Duration.ofDays(refreshTokenExpirationDays);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public String issue(UUID userId) {
        String rawToken = generateRawToken();
        RefreshToken refreshToken = new RefreshToken(
                UUID.randomUUID(),
                userId,
                hash(rawToken),
                Instant.now().plus(refreshTokenValidity),
                false,
                null,
                UUID.randomUUID(),
                Instant.now()
        );
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    public RotationResult validateAndRotate(String rawToken) {
        String tokenHash = hash(rawToken);
        RefreshToken existing = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (existing.revoked()) {
            boolean rotatedRecently = existing.revokedAt() != null
                    && existing.revokedAt().isAfter(Instant.now().minus(ROTATION_GRACE_PERIOD));
            if (rotatedRecently) {
                throw new InvalidRefreshTokenException();
            }
            refreshTokenRepository.revokeFamily(existing.familyId());
            throw new InvalidRefreshTokenException();
        }

        if (existing.expiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }

        String newRawToken = generateRawToken();
        Instant newExpiresAt = Instant.now().plus(refreshTokenValidity);
        Optional<UUID> rotatedUserId =
                refreshTokenRepository.rotate(tokenHash, hash(newRawToken), newExpiresAt);
        if (rotatedUserId.isEmpty()) {
            throw new InvalidRefreshTokenException();
        }
        return new RotationResult(rotatedUserId.get(), newRawToken);
    }

    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.revokeByTokenHash(hash(rawToken));
    }

    public record RotationResult(UUID userId, String newRawToken) {}
}
