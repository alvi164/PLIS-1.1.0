package edu.university.plis.shared.dto;

import edu.university.plis.shared.model.DeviceConnectionStatus;

import java.time.Instant;

public record ConnectionRequestView(
        long id,
        long studentId,
        String studentUsername,
        String studentName,
        String deviceId,
        String deviceName,
        DeviceConnectionStatus status,
        Instant requestedAt,
        Instant approvedAt,
        Instant lastSeenAt,
        Instant terminatedAt
) {
}
