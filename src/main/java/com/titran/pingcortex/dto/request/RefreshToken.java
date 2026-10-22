package com.titran.pingcortex.dto.request;

import java.time.Instant;
import java.util.UUID;

public record RefreshToken(
        UUID id,
        UUID userId,
        String tokenHash,
        Instant expiresAt,
        boolean revoked,
        Instant revokedAt,
        UUID familyId,
        Instant createdAt
) {
}
