package edu.university.plis.shared.dto;

import edu.university.plis.shared.model.IntegrityFlagType;

import java.time.Instant;

public record IntegrityFlagView(
        long id,
        long examSessionId,
        long studentId,
        String studentName,
        IntegrityFlagType flagType,
        String description,
        Instant timestamp
) {
}
