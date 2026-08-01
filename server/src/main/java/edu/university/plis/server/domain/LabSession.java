package edu.university.plis.server.domain;

import edu.university.plis.shared.model.LabSessionStatus;
import edu.university.plis.shared.model.NetworkConnectionType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "lab_sessions")
public class LabSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_code", nullable = false, length = 40)
    private String courseCode;

    @Column(nullable = false, length = 40)
    private String section;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_lab_teacher"))
    private TeacherProfile teacher;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LabSessionStatus status = LabSessionStatus.DRAFT;

    @Column(name = "max_participants")
    private Integer maxParticipants;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_policy", length = 20)
    private NetworkConnectionType connectionPolicy;

    @ManyToMany
    @JoinTable(name = "lab_session_students",
            joinColumns = @JoinColumn(name = "lab_session_id"),
            inverseJoinColumns = @JoinColumn(name = "student_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_lab_student", columnNames = {"lab_session_id", "student_id"}))
    private Set<StudentProfile> assignedStudents = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(name = "lab_session_joined_students",
            joinColumns = @JoinColumn(name = "lab_session_id"),
            inverseJoinColumns = @JoinColumn(name = "student_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_lab_joined_student",
                    columnNames = {"lab_session_id", "student_id"}))
    private Set<StudentProfile> joinedStudents = new LinkedHashSet<>();

    protected LabSession() {
    }

    public LabSession(
            String courseCode,
            String section,
            TeacherProfile teacher,
            Set<StudentProfile> assignedStudents,
            int maxParticipants,
            NetworkConnectionType connectionPolicy) {
        this.courseCode = courseCode;
        this.section = section;
        this.teacher = teacher;
        this.assignedStudents.addAll(assignedStudents);
        this.maxParticipants = maxParticipants;
        this.connectionPolicy = connectionPolicy;
    }

    public void start(Instant now) {
        if (status != LabSessionStatus.DRAFT) {
            throw new IllegalStateException("Only a draft lab session can be started");
        }
        status = LabSessionStatus.ACTIVE;
        startTime = now;
    }

    public void end(Instant now) {
        if (status != LabSessionStatus.ACTIVE) {
            throw new IllegalStateException("Only an active lab session can be ended");
        }
        status = LabSessionStatus.ENDED;
        endTime = now;
    }

    public boolean isAssigned(StudentProfile student) {
        return student != null && student.getId() != null
                && assignedStudents.stream().anyMatch(assigned -> assigned.getId().equals(student.getId()));
    }

    public boolean join(StudentProfile student) {
        if (isJoined(student)) {
            return false;
        }
        if (joinedStudents.size() >= getMaxParticipants()) {
            throw new IllegalStateException("This lab has reached its teacher-configured participant limit");
        }
        joinedStudents.add(student);
        return true;
    }

    public boolean isJoined(StudentProfile student) {
        return student != null && student.getId() != null
                && joinedStudents.stream().anyMatch(joined -> joined.getId().equals(student.getId()));
    }

    public void updatePolicy(int newMaxParticipants, NetworkConnectionType newConnectionPolicy) {
        if (status == LabSessionStatus.ENDED) {
            throw new IllegalStateException("An ended lab session cannot be changed");
        }
        if (newMaxParticipants < joinedStudents.size()) {
            throw new IllegalStateException("The participant limit cannot be below the number already joined");
        }
        if (newMaxParticipants < 1 || newMaxParticipants > assignedStudents.size()) {
            throw new IllegalStateException("The participant limit must be between 1 and the assigned student count");
        }
        maxParticipants = newMaxParticipants;
        connectionPolicy = newConnectionPolicy;
    }

    public Long getId() {
        return id;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public String getSection() {
        return section;
    }

    public TeacherProfile getTeacher() {
        return teacher;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public LabSessionStatus getStatus() {
        return status;
    }

    public Set<StudentProfile> getAssignedStudents() {
        return Set.copyOf(assignedStudents);
    }

    public int getMaxParticipants() {
        return maxParticipants == null ? assignedStudents.size() : maxParticipants;
    }

    public NetworkConnectionType getConnectionPolicy() {
        return connectionPolicy == null ? NetworkConnectionType.ANY : connectionPolicy;
    }

    public Set<StudentProfile> getJoinedStudents() {
        return Set.copyOf(joinedStudents);
    }
}
