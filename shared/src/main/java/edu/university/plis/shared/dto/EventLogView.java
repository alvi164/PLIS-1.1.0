package edu.university.plis.shared.dto;

import edu.university.plis.shared.model.SessionType;
import edu.university.plis.shared.model.StudentEventType;

import java.time.Instant;

public record EventLogView(
        long id,
        SessionType sessionType,
        long sessionId,
        long studentId,
        String studentName,
        StudentEventType eventType,
        String payload,
        Instant timestamp
) {
}
