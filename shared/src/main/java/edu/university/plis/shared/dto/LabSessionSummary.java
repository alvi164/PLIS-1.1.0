package edu.university.plis.shared.dto;

import edu.university.plis.shared.model.LabSessionStatus;
import edu.university.plis.shared.model.NetworkConnectionType;

import java.time.Instant;

public record LabSessionSummary(
        long id,
        String courseCode,
        String section,
        String teacherName,
        Instant startTime,
        Instant endTime,
        LabSessionStatus status,
        int assignedStudentCount,
        int maxParticipants,
        int joinedStudentCount,
        NetworkConnectionType connectionPolicy
) {
    @Override
    public String toString() {
        return courseCode + " / " + section + " [" + status + "] "
                + joinedStudentCount + "/" + maxParticipants;
    }
}
