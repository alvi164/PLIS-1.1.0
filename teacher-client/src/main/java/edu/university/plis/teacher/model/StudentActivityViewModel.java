package edu.university.plis.teacher.model;

import edu.university.plis.shared.dto.StudentActivitySnapshot;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public record StudentActivityViewModel(StudentActivitySnapshot snapshot) {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public String heading() {
        return snapshot.studentNumber() + " · " + snapshot.studentName();
    }

    public String statusText() {
        return snapshot.status().name().replace('_', ' ');
    }

    public String lastEventText() {
        return snapshot.lastEventAt() == null ? "No activity yet" : "Last event " + TIME.format(snapshot.lastEventAt());
    }
}
