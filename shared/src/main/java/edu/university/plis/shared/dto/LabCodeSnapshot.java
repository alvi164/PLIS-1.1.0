package edu.university.plis.shared.dto;

import edu.university.plis.shared.model.CodeAuthorType;

import java.time.Instant;

public record LabCodeSnapshot(
        long labSessionId,
        long studentId,
        String studentName,
        String codeText,
        long revision,
        CodeAuthorType lastAuthorType,
        String lastAuthorName,
        Instant modifiedAt
) {
}
