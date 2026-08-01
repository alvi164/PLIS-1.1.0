package edu.university.plis.shared.dto;

public record StudentSummary(long id, String studentNumber, String displayName, String program, Integer semester) {
    @Override
    public String toString() {
        return studentNumber + " — " + displayName;
    }
}
