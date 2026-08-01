package edu.university.plis.shared.dto;

import edu.university.plis.shared.model.ExamSessionStatus;

import java.time.Instant;

public record ExamSummary(
        long id,
        String examTitle,
        String courseCode,
        Instant scheduledStartTime,
        int durationMinutes,
        ExamSessionStatus status,
        int assignedStudentCount
) {
    @Override
    public String toString() {
        return examTitle + " — " + courseCode + " [" + status + "]";
    }
}
