package edu.university.plis.server.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "students", uniqueConstraints =
        @UniqueConstraint(name = "uk_student_number", columnNames = "student_number"))
public class StudentProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_student_user"))
    private AppUser user;

    @Column(name = "student_number", nullable = false, length = 40)
    private String studentNumber;

    @Column(length = 120)
    private String program;

    private Integer semester;

    protected StudentProfile() {
    }

    public StudentProfile(AppUser user, String studentNumber, String program, Integer semester) {
        this.user = user;
        this.studentNumber = studentNumber;
        this.program = program;
        this.semester = semester;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public String getStudentNumber() {
        return studentNumber;
    }

    public String getProgram() {
        return program;
    }

    public Integer getSemester() {
        return semester;
    }
}
