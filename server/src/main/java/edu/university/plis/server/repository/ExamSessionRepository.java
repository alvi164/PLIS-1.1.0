package edu.university.plis.server.repository;

import edu.university.plis.server.domain.ExamSession;
import edu.university.plis.shared.model.ExamSessionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExamSessionRepository extends JpaRepository<ExamSession, Long> {
    @Override
    @EntityGraph(attributePaths = {"createdBy.user", "assignedStudents", "assignedStudents.user"})
    Optional<ExamSession> findById(Long id);

    @EntityGraph(attributePaths = {"createdBy.user", "assignedStudents"})
    List<ExamSession> findAllByOrderByScheduledStartTimeDesc();

    @EntityGraph(attributePaths = {"createdBy.user", "assignedStudents"})
    List<ExamSession> findByCreatedByUserUsernameIgnoreCaseOrderByScheduledStartTimeDesc(String username);

    @EntityGraph(attributePaths = {"createdBy.user", "assignedStudents"})
    List<ExamSession> findDistinctByAssignedStudentsIdAndStatusInOrderByScheduledStartTimeAsc(
            Long studentId, Collection<ExamSessionStatus> statuses);
}
