package edu.university.plis.shared.dto;

import edu.university.plis.shared.model.ProgrammingLanguage;

import java.time.Instant;

public record ExamCodeUpdateRequest(
        ProgrammingLanguage language,
        String codeText,
        long baseRevision,
        Instant editingStartedAt
) {
}
