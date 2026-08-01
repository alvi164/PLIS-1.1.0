package edu.university.plis.shared.dto;

public record CreateStudentAccountRequest(
        String username,
        String displayName,
        String studentNumber,
        String program,
        Integer semester,
        String initialPassword
) {
}
