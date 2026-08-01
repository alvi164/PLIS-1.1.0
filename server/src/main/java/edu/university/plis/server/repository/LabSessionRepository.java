package edu.university.plis.server.repository;

import edu.university.plis.server.domain.LabSession;
import edu.university.plis.shared.model.LabSessionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LabSessionRepository extends JpaRepository<LabSession, Long> {
    @Override
    @EntityGraph(attributePaths = {"teacher.user", "assignedStudents", "joinedStudents"})
    Optional<LabSession> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"teacher.user", "assignedStudents", "joinedStudents"})
    @Query("select session from LabSession session where session.id = :sessionId")
    Optional<LabSession> findByIdForUpdate(@Param("sessionId") Long sessionId);

    @EntityGraph(attributePaths = {"teacher.user", "assignedStudents", "joinedStudents"})
    List<LabSession> findAllByOrderByIdDesc();

    @EntityGraph(attributePaths = {"teacher.user", "assignedStudents", "joinedStudents"})
    List<LabSession> findByTeacherUserUsernameIgnoreCaseOrderByIdDesc(String username);

    @EntityGraph(attributePaths = {"teacher.user", "assignedStudents", "joinedStudents"})
    List<LabSession> findDistinctByAssignedStudentsIdAndStatusInOrderByIdDesc(
            Long studentId, Collection<LabSessionStatus> statuses);
}
