package edu.university.plis.server.domain;

import edu.university.plis.shared.model.SessionType;
import edu.university.plis.shared.model.StudentEventType;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "event_logs", indexes = {
        @Index(name = "idx_event_session_student_time", columnList = "session_type,session_id,student_id,event_timestamp"),
        @Index(name = "idx_event_type", columnList = "session_type,session_id,event_type")
})
public class EventLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false, length = 12)
    private SessionType sessionType;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_event_student"))
    private StudentProfile student;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private StudentEventType eventType;

    @Lob
    private String payload;

    @Column(name = "event_timestamp", nullable = false)
    private Instant timestamp;

    protected EventLog() {
    }

    public EventLog(
            SessionType sessionType,
            Long sessionId,
            StudentProfile student,
            StudentEventType eventType,
            String payload,
            Instant timestamp) {
        this.sessionType = sessionType;
        this.sessionId = sessionId;
        this.student = student;
        this.eventType = eventType;
        this.payload = payload;
        this.timestamp = timestamp;
    }

    public Long getId() {
        return id;
    }

    public SessionType getSessionType() {
        return sessionType;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public StudentProfile getStudent() {
        return student;
    }

    public StudentEventType getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
