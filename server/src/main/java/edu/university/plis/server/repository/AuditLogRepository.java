package edu.university.plis.server.repository;

import edu.university.plis.server.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findTop500ByOrderByTimestampDesc();
    List<AuditLog> findTop500ByActorUsernameIgnoreCaseOrderByTimestampDesc(String actorUsername);
}
