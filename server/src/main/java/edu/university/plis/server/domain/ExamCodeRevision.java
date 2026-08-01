package edu.university.plis.server.domain;

import edu.university.plis.shared.model.ProgrammingLanguage;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "exam_code_revisions", indexes = @Index(
        name = "idx_exam_code_revision", columnList = "exam_session_id,student_id,revision_number"),
        uniqueConstraints = @UniqueConstraint(name = "uk_exam_student_code_revision",
                columnNames = {"exam_session_id", "student_id", "revision_number"}))
public class ExamCodeRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_session_id", nullable = false)
    private ExamSession examSession;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @Column(name = "revision_number", nullable = false)
    private long revisionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "programming_language", nullable = false, length = 24)
    private ProgrammingLanguage language;

    @Lob
    @Column(name = "code_text", nullable = false)
    private String codeText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ExamCodeRevision() {
    }

    public ExamCodeRevision(ExamCodeDocument document) {
        examSession = document.getExamSession();
        student = document.getStudent();
        revisionNumber = document.getRevision();
        language = document.getLanguage();
        codeText = document.getCodeText();
        createdAt = document.getModifiedAt();
    }
}
