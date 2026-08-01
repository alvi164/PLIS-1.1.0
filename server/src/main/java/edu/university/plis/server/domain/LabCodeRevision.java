package edu.university.plis.server.domain;

import edu.university.plis.shared.model.CodeAuthorType;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "lab_code_revisions", indexes = @Index(
        name = "idx_lab_code_revision", columnList = "lab_session_id,student_id,revision_number"),
        uniqueConstraints = @UniqueConstraint(name = "uk_lab_student_revision",
                columnNames = {"lab_session_id", "student_id", "revision_number"}))
public class LabCodeRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_session_id", nullable = false)
    private LabSession labSession;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @Column(name = "revision_number", nullable = false)
    private long revisionNumber;

    @Lob
    @Column(name = "code_text", nullable = false)
    private String codeText;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_type", nullable = false, length = 12)
    private CodeAuthorType authorType;

    @Column(name = "author_name", nullable = false, length = 120)
    private String authorName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LabCodeRevision() {
    }

    public LabCodeRevision(LabCodeDocument document) {
        labSession = document.getLabSession();
        student = document.getStudent();
        revisionNumber = document.getRevision();
        codeText = document.getCodeText();
        authorType = document.getLastAuthorType();
        authorName = document.getLastAuthorName();
        createdAt = document.getModifiedAt();
    }
}
