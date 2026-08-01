package edu.university.plis.shared.dto;

import edu.university.plis.shared.model.StudentEventType;

public record LabEventRequest(StudentEventType eventType, String payload) {
}
