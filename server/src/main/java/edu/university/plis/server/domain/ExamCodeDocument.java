package edu.university.plis.server.domain;

import edu.university.plis.shared.model.ProgrammingLanguage;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "exam_code_documents", uniqueConstraints = @UniqueConstraint(
        name = "uk_exam_code_student", columnNames = {"exam_session_id", "student_id"}))
public class ExamCodeDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_session_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_exam_code_exam"))
    private ExamSession examSession;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_exam_code_student"))
    private StudentProfile student;

    @Enumerated(EnumType.STRING)
    @Column(name = "programming_language", length = 24)
    private ProgrammingLanguage language;

    @Lob
    @Column(name = "code_text", nullable = false)
    private String codeText = "";

    @Column(nullable = false)
    private long revision;

    @Column(name = "editing_started_at")
    private Instant editingStartedAt;

    @Column(name = "modified_at", nullable = false)
    private Instant modifiedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    protected ExamCodeDocument() {
    }

    public ExamCodeDocument(ExamSession examSession, StudentProfile student, Instant now) {
        this.examSession = examSession;
        this.student = student;
        this.modifiedAt = now;
    }

    public void update(ProgrammingLanguage language, String codeText, Instant editingStartedAt, Instant now) {
        this.language = language;
        this.codeText = codeText;
        if (this.editingStartedAt == null) {
            this.editingStartedAt = editingStartedAt == null ? now : editingStartedAt;
        }
        this.modifiedAt = now;
        revision++;
    }

    public void markSubmitted(Instant submittedAt) {
        this.submittedAt = submittedAt;
        this.modifiedAt = submittedAt;
    }

    public Long getId() { return id; }
    public ExamSession getExamSession() { return examSession; }
    public StudentProfile getStudent() { return student; }
    public ProgrammingLanguage getLanguage() { return language; }
    public String getCodeText() { return codeText; }
    public long getRevision() { return revision; }
    public Instant getEditingStartedAt() { return editingStartedAt; }
    public Instant getModifiedAt() { return modifiedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
}
