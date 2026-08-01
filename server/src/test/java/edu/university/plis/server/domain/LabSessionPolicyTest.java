package edu.university.plis.server.domain;

import edu.university.plis.shared.model.NetworkConnectionType;
import edu.university.plis.shared.model.RoleName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.LinkedHashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LabSessionPolicyTest {
    @Test
    void enforcesCapacityAndAllowsTeacherToRaiseItDuringClass() {
        TeacherProfile teacher = teacher();
        StudentProfile first = student(1L, "student01", "CSE-001");
        StudentProfile twelfth = student(12L, "student12", "CSE-012");
        LabSession session = new LabSession("CSE-101", "A", teacher,
                new LinkedHashSet<>(java.util.List.of(first, twelfth)), 1, NetworkConnectionType.ANY);
        ReflectionTestUtils.setField(session, "id", 10L);
        session.start(Instant.now());

        assertThat(session.join(first)).isTrue();
        assertThatThrownBy(() -> session.join(twelfth))
                .hasMessageContaining("participant limit");

        session.updatePolicy(2, NetworkConnectionType.WIFI);
        assertThat(session.join(twelfth)).isTrue();
        assertThat(session.getJoinedStudents()).hasSize(2);
        assertThat(session.getConnectionPolicy()).isEqualTo(NetworkConnectionType.WIFI);
    }

    private TeacherProfile teacher() {
        AppUser user = new AppUser("teacher", "hash", RoleName.ROLE_TEACHER, "Teacher");
        ReflectionTestUtils.setField(user, "id", 100L);
        TeacherProfile teacher = new TeacherProfile(user, "Computer Science");
        ReflectionTestUtils.setField(teacher, "id", 100L);
        return teacher;
    }

    private StudentProfile student(long id, String username, String number) {
        AppUser user = new AppUser(username, "hash", RoleName.ROLE_STUDENT, username);
        ReflectionTestUtils.setField(user, "id", id);
        StudentProfile student = new StudentProfile(user, number, "CSE", 1);
        ReflectionTestUtils.setField(student, "id", id);
        return student;
    }
}
