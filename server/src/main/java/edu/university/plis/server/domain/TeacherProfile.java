package edu.university.plis.server.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "teachers")
public class TeacherProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_teacher_user"))
    private AppUser user;

    @Column(length = 120)
    private String department;

    protected TeacherProfile() {
    }

    public TeacherProfile(AppUser user, String department) {
        this.user = user;
        this.department = department;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public String getDepartment() {
        return department;
    }
}
