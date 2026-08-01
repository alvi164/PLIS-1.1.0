package edu.university.plis.shared.dto;

import edu.university.plis.shared.model.ExamSessionStatus;

import java.time.Instant;

public record ExamDetails(
        long id,
        String examTitle,
        String courseCode,
        Instant scheduledStartTime,
        Instant actualStartTime,
        Instant endedAt,
        int durationMinutes,
        String questionText,
        ExamSessionStatus status
) {
}
