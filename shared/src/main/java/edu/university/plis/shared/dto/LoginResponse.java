package edu.university.plis.shared.dto;

import edu.university.plis.shared.model.RoleName;

import java.time.Instant;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        long userId,
        Long profileId,
        String username,
        String displayName,
        RoleName role
) {
}
