package edu.university.plis.shared.dto;

import edu.university.plis.shared.model.StudentActivityStatus;
import edu.university.plis.shared.model.NetworkConnectionType;

import java.time.Instant;

public record StudentActivitySnapshot(
        long labSessionId,
        long studentId,
        String studentNumber,
        String studentName,
        StudentActivityStatus status,
        Instant lastEventAt,
        String detail,
        NetworkConnectionType connectionType,
        long codeRevision
) {
}
