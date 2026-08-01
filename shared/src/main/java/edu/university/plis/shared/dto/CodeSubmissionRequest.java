package edu.university.plis.shared.dto;

import edu.university.plis.shared.model.ProgrammingLanguage;

import java.time.Instant;

public record CodeSubmissionRequest(
        String codeText,
        Instant editingStartedAt,
        ProgrammingLanguage language
) {
    public CodeSubmissionRequest(String codeText, Instant editingStartedAt) {
        this(codeText, editingStartedAt, ProgrammingLanguage.JAVA);
    }
}
