package edu.university.plis.server.repository;

import edu.university.plis.server.domain.ExamCodeRevision;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamCodeRevisionRepository extends JpaRepository<ExamCodeRevision, Long> {
}
