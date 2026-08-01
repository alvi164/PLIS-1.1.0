package edu.university.plis.server.repository;

import edu.university.plis.server.domain.EventLog;
import edu.university.plis.shared.model.SessionType;
import edu.university.plis.shared.model.StudentEventType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventLogRepository extends JpaRepository<EventLog, Long> {
    @EntityGraph(attributePaths = {"student", "student.user"})
    List<EventLog> findBySessionTypeAndSessionIdAndStudentIdOrderByTimestampDesc(
            SessionType sessionType, Long sessionId, Long studentId);

    long countBySessionTypeAndSessionIdAndStudentIdAndEventType(
            SessionType sessionType, Long sessionId, Long studentId, StudentEventType eventType);

    @EntityGraph(attributePaths = {"student", "student.user"})
    Optional<EventLog> findFirstBySessionTypeAndSessionIdAndStudentIdOrderByTimestampDesc(
            SessionType sessionType, Long sessionId, Long studentId);

    @EntityGraph(attributePaths = {"student", "student.user"})
    List<EventLog> findTop1000ByStudentIdOrderByTimestampDesc(Long studentId);
}
