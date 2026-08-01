package edu.university.plis.shared.dto;

import java.time.Instant;
import java.util.Set;

public record CreateExamRequest(
        String examTitle,
        String courseCode,
        Instant scheduledStartTime,
        int durationMinutes,
        String questionText,
        Set<Long> assignedStudentIds
) {
}
