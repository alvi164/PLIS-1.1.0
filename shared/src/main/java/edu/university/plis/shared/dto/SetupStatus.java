package edu.university.plis.shared.dto;

public record SetupStatus(
        boolean setupRequired,
        String installationId,
        String teacherName,
        String productVersion
) {
}
