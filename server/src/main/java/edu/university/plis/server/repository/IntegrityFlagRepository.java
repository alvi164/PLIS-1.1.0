package edu.university.plis.server.repository;

import edu.university.plis.server.domain.IntegrityFlag;
import edu.university.plis.shared.model.IntegrityFlagType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IntegrityFlagRepository extends JpaRepository<IntegrityFlag, Long> {
    @EntityGraph(attributePaths = {"student", "student.user", "examSession"})
    List<IntegrityFlag> findByExamSessionIdOrderByTimestampAsc(Long examSessionId);

    long countByExamSessionIdAndStudentId(Long examSessionId, Long studentId);

    boolean existsByExamSessionIdAndStudentIdAndFlagType(
            Long examSessionId, Long studentId, IntegrityFlagType flagType);
}
