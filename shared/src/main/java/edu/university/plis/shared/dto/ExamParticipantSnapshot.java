package edu.university.plis.shared.dto;

import edu.university.plis.shared.model.ExamParticipantState;
import edu.university.plis.shared.model.ProgrammingLanguage;

import java.time.Instant;

public record ExamParticipantSnapshot(
        long examSessionId,
        long studentId,
        String studentNumber,
        String studentName,
        ExamParticipantState state,
        Instant lastEventAt,
        long disconnectCount,
        long focusLossCount,
        int integrityFlagCount,
        ProgrammingLanguage language,
        String fileName,
        long codeRevision
) {
}
