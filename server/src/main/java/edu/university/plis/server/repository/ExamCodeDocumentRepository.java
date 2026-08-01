package edu.university.plis.server.repository;

import edu.university.plis.server.domain.ExamCodeDocument;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ExamCodeDocumentRepository extends JpaRepository<ExamCodeDocument, Long> {
    @EntityGraph(attributePaths = {"examSession", "student", "student.user"})
    Optional<ExamCodeDocument> findByExamSessionIdAndStudentId(Long examSessionId, Long studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"examSession", "student", "student.user"})
    @Query("select document from ExamCodeDocument document "
            + "where document.examSession.id = :examId and document.student.id = :studentId")
    Optional<ExamCodeDocument> findForUpdate(@Param("examId") Long examId, @Param("studentId") Long studentId);
}
