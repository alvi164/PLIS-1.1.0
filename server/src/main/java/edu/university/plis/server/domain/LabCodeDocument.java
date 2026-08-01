package edu.university.plis.server.domain;

import edu.university.plis.shared.model.CodeAuthorType;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "lab_code_documents", uniqueConstraints = @UniqueConstraint(
        name = "uk_lab_code_student", columnNames = {"lab_session_id", "student_id"}))
public class LabCodeDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_session_id", nullable = false)
    private LabSession labSession;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @Lob
    @Column(name = "code_text", nullable = false)
    private String codeText = "";

    @Column(nullable = false)
    private long revision;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_author_type", nullable = false, length = 12)
    private CodeAuthorType lastAuthorType = CodeAuthorType.STUDENT;

    @Column(name = "last_author_name", nullable = false, length = 120)
    private String lastAuthorName;

    @Column(name = "modified_at", nullable = false)
    private Instant modifiedAt;

    protected LabCodeDocument() {
    }

    public LabCodeDocument(LabSession labSession, StudentProfile student, String authorName, Instant now) {
        this.labSession = labSession;
        this.student = student;
        this.lastAuthorName = authorName;
        this.modifiedAt = now;
    }

    public void update(String newCode, CodeAuthorType authorType, String authorName, Instant now) {
        codeText = newCode;
        revision++;
        lastAuthorType = authorType;
        lastAuthorName = authorName;
        modifiedAt = now;
    }

    public Long getId() { return id; }
    public LabSession getLabSession() { return labSession; }
    public StudentProfile getStudent() { return student; }
    public String getCodeText() { return codeText; }
    public long getRevision() { return revision; }
    public CodeAuthorType getLastAuthorType() { return lastAuthorType; }
    public String getLastAuthorName() { return lastAuthorName; }
    public Instant getModifiedAt() { return modifiedAt; }
}
