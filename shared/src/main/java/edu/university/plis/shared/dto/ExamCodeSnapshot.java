package edu.university.plis.shared.dto;

import edu.university.plis.shared.model.ProgrammingLanguage;

import java.time.Instant;

public record ExamCodeSnapshot(
        long examSessionId,
        long studentId,
        String studentNumber,
        String studentName,
        ProgrammingLanguage language,
        String fileName,
        String codeText,
        long revision,
        Instant editingStartedAt,
        Instant modifiedAt,
        boolean submitted,
        Instant submittedAt
) {
}
