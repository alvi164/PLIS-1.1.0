package edu.university.plis.server.repository;

import edu.university.plis.server.domain.StudentProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {
    Optional<StudentProfile> findByUserUsernameIgnoreCase(String username);

    @EntityGraph(attributePaths = "user")
    List<StudentProfile> findAllByOrderByStudentNumberAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select student from StudentProfile student join fetch student.user where student.id = :studentId")
    Optional<StudentProfile> findByIdForEventUpdate(@Param("studentId") Long studentId);
}
