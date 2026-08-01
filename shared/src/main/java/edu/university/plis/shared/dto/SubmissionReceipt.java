package edu.university.plis.shared.dto;

import java.time.Instant;

public record SubmissionReceipt(long submissionId, Instant submittedAt, boolean accepted) {
}
