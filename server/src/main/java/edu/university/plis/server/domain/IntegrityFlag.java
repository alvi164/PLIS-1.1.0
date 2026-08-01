package edu.university.plis.server.domain;

import edu.university.plis.shared.model.IntegrityFlagType;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "integrity_flags",
        indexes = @Index(name = "idx_flag_exam_student", columnList = "exam_session_id,student_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_flag_exam_student_type",
                columnNames = {"exam_session_id", "student_id", "flag_type"}))
public class IntegrityFlag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_session_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_flag_exam"))
    private ExamSession examSession;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_flag_student"))
    private StudentProfile student;

    @Enumerated(EnumType.STRING)
    @Column(name = "flag_type", nullable = false, length = 40)
    private IntegrityFlagType flagType;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "flag_timestamp", nullable = false)
    private Instant timestamp;

    protected IntegrityFlag() {
    }

    public IntegrityFlag(
            ExamSession examSession,
            StudentProfile student,
            IntegrityFlagType flagType,
            String description,
            Instant timestamp) {
        this.examSession = examSession;
        this.student = student;
        this.flagType = flagType;
        this.description = description;
        this.timestamp = timestamp;
    }

    public Long getId() {
        return id;
    }

    public ExamSession getExamSession() {
        return examSession;
    }

    public StudentProfile getStudent() {
        return student;
    }

    public IntegrityFlagType getFlagType() {
        return flagType;
    }

    public String getDescription() {
        return description;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
