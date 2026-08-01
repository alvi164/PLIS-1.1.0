package edu.university.plis.server.domain;

import edu.university.plis.shared.model.ExamSessionStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "exam_sessions")
public class ExamSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exam_title", nullable = false, length = 160)
    private String examTitle;

    @Column(name = "course_code", nullable = false, length = 40)
    private String courseCode;

    @Column(name = "scheduled_start_time", nullable = false)
    private Instant scheduledStartTime;

    @Column(name = "actual_start_time")
    private Instant actualStartTime;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Lob
    @Column(name = "question_text", nullable = false)
    private String questionText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ExamSessionStatus status = ExamSessionStatus.DRAFT;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_teacher_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_exam_teacher"))
    private TeacherProfile createdBy;

    @ManyToMany
    @JoinTable(name = "exam_session_students",
            joinColumns = @JoinColumn(name = "exam_session_id"),
            inverseJoinColumns = @JoinColumn(name = "student_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_exam_student", columnNames = {"exam_session_id", "student_id"}))
    private Set<StudentProfile> assignedStudents = new LinkedHashSet<>();

    protected ExamSession() {
    }

    public ExamSession(
            String examTitle,
            String courseCode,
            Instant scheduledStartTime,
            int durationMinutes,
            String questionText,
            TeacherProfile createdBy,
            Set<StudentProfile> assignedStudents) {
        this.examTitle = examTitle;
        this.courseCode = courseCode;
        this.scheduledStartTime = scheduledStartTime;
        this.durationMinutes = durationMinutes;
        this.questionText = questionText;
        this.createdBy = createdBy;
        this.assignedStudents.addAll(assignedStudents);
        this.status = ExamSessionStatus.SCHEDULED;
    }

    public void start(Instant now) {
        if (status != ExamSessionStatus.SCHEDULED && status != ExamSessionStatus.DRAFT) {
            throw new IllegalStateException("Only a scheduled exam can be started");
        }
        status = ExamSessionStatus.ACTIVE;
        actualStartTime = now;
    }

    public void end(Instant now) {
        if (status != ExamSessionStatus.ACTIVE) {
            throw new IllegalStateException("Only an active exam can be ended");
        }
        status = ExamSessionStatus.ENDED;
        endedAt = now;
    }

    public boolean isAssigned(StudentProfile student) {
        return student != null && student.getId() != null
                && assignedStudents.stream().anyMatch(assigned -> assigned.getId().equals(student.getId()));
    }

    public Long getId() {
        return id;
    }

    public String getExamTitle() {
        return examTitle;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public Instant getScheduledStartTime() {
        return scheduledStartTime;
    }

    public Instant getActualStartTime() {
        return actualStartTime;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public String getQuestionText() {
        return questionText;
    }

    public ExamSessionStatus getStatus() {
        return status;
    }

    public TeacherProfile getCreatedBy() {
        return createdBy;
    }

    public Set<StudentProfile> getAssignedStudents() {
        return Set.copyOf(assignedStudents);
    }
}
