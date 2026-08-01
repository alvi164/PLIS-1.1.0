package edu.university.plis.server.repository;

import edu.university.plis.server.domain.TeacherProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;

public interface TeacherProfileRepository extends JpaRepository<TeacherProfile, Long> {
    Optional<TeacherProfile> findByUserUsernameIgnoreCase(String username);

    @EntityGraph(attributePaths = "user")
    Optional<TeacherProfile> findFirstByOrderByIdAsc();
}
