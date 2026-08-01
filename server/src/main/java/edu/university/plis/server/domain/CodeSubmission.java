package edu.university.plis.server.domain;

import edu.university.plis.shared.model.ProgrammingLanguage;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "code_submissions", uniqueConstraints =
        @UniqueConstraint(name = "uk_submission_exam_student", columnNames = {"exam_session_id", "student_id"}))
public class CodeSubmission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_session_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_submission_exam"))
    private ExamSession examSession;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_submission_student"))
    private StudentProfile student;

    @Lob
    @Column(name = "code_text", nullable = false)
    private String codeText;

    @Enumerated(EnumType.STRING)
    @Column(name = "programming_language", length = 24)
    private ProgrammingLanguage language;

    @Column(name = "editing_started_at")
    private Instant editingStartedAt;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    protected CodeSubmission() {
    }

    public CodeSubmission(
            ExamSession examSession,
            StudentProfile student,
            String codeText,
            ProgrammingLanguage language,
            Instant editingStartedAt,
            Instant submittedAt) {
        this.examSession = examSession;
        this.student = student;
        this.codeText = codeText;
        this.language = language;
        this.editingStartedAt = editingStartedAt;
        this.submittedAt = submittedAt;
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

    public String getCodeText() {
        return codeText;
    }

    public ProgrammingLanguage getLanguage() {
        return language == null ? ProgrammingLanguage.JAVA : language;
    }

    public Instant getEditingStartedAt() {
        return editingStartedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
