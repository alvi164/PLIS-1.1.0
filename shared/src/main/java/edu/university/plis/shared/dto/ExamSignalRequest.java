package edu.university.plis.shared.dto;

import edu.university.plis.shared.model.StudentEventType;

public record ExamSignalRequest(StudentEventType signalType, String detail) {
}
