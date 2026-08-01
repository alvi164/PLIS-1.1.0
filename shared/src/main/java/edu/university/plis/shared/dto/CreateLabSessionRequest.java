package edu.university.plis.shared.dto;

import edu.university.plis.shared.model.NetworkConnectionType;

import java.util.Set;

public record CreateLabSessionRequest(
        String courseCode,
        String section,
        Set<Long> assignedStudentIds,
        int maxParticipants,
        NetworkConnectionType connectionPolicy
) {
}
