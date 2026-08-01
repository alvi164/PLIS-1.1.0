package edu.university.plis.shared.dto;

import edu.university.plis.shared.model.RoleName;

import java.time.Instant;

public record AuditLogView(
        long id,
        String actorUsername,
        RoleName actorRole,
        String action,
        String details,
        String deviceId,
        Instant timestamp
) {
}
