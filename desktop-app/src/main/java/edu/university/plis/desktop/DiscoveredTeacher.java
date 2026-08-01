package edu.university.plis.desktop;

import java.time.Instant;

record DiscoveredTeacher(
        String installationId,
        String teacherName,
        String serverUrl,
        Instant lastSeen
) {
    @Override
    public String toString() {
        return teacherName + "\n" + serverUrl;
    }
}
