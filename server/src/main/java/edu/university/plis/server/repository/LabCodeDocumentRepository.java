package edu.university.plis.server.repository;

import edu.university.plis.server.domain.LabCodeDocument;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LabCodeDocumentRepository extends JpaRepository<LabCodeDocument, Long> {
    @EntityGraph(attributePaths = {"labSession.teacher.user", "student.user"})
    Optional<LabCodeDocument> findByLabSessionIdAndStudentId(Long labSessionId, Long studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"labSession.teacher.user", "student.user"})
    @Query("select document from LabCodeDocument document "
            + "where document.labSession.id = :labSessionId and document.student.id = :studentId")
    Optional<LabCodeDocument> findForUpdate(
            @Param("labSessionId") Long labSessionId,
            @Param("studentId") Long studentId);
}
