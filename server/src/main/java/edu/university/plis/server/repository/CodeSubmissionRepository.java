package edu.university.plis.server.repository;

import edu.university.plis.server.domain.CodeSubmission;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CodeSubmissionRepository extends JpaRepository<CodeSubmission, Long> {
    @EntityGraph(attributePaths = {"student", "student.user", "examSession"})
    List<CodeSubmission> findByExamSessionIdOrderBySubmittedAtAsc(Long examSessionId);

    @EntityGraph(attributePaths = {"student", "student.user", "examSession"})
    Optional<CodeSubmission> findByExamSessionIdAndStudentId(Long examSessionId, Long studentId);

    boolean existsByExamSessionIdAndStudentId(Long examSessionId, Long studentId);
}
