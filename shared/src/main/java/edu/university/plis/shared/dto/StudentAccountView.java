package edu.university.plis.shared.dto;

public record StudentAccountView(
        long profileId,
        long userId,
        String username,
        String displayName,
        String studentNumber,
        String program,
        Integer semester,
        boolean enabled,
        long approvedDeviceCount
) {
    @Override
    public String toString() {
        return studentNumber + " - " + displayName + " (" + username + ")";
    }
}
