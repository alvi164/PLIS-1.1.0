package edu.university.plis.server.service;

import edu.university.plis.server.domain.AuditLog;
import edu.university.plis.server.repository.AuditLogRepository;
import edu.university.plis.server.repository.EventLogRepository;
import edu.university.plis.shared.dto.AuditLogView;
import edu.university.plis.shared.dto.EventLogView;
import edu.university.plis.shared.model.RoleName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AuditService {
    private final AuditLogRepository auditRepository;
    private final EventLogRepository eventRepository;

    public AuditService(AuditLogRepository auditRepository, EventLogRepository eventRepository) {
        this.auditRepository = auditRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public void record(String username, RoleName role, String action, String details, String deviceId) {
        auditRepository.save(new AuditLog(clean(username, 80), role, clean(action, 80),
                clean(details, 2_000), clean(deviceId, 128), Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<AuditLogView> recent(String username) {
        return (username == null || username.isBlank()
                ? auditRepository.findTop500ByOrderByTimestampDesc()
                : auditRepository.findTop500ByActorUsernameIgnoreCaseOrderByTimestampDesc(username)).stream()
                .map(this::view)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventLogView> studentSessionEvents(long studentId) {
        return eventRepository.findTop1000ByStudentIdOrderByTimestampDesc(studentId).stream()
                .map(ViewMapper::toEventView)
                .toList();
    }

    private AuditLogView view(AuditLog log) {
        return new AuditLogView(log.getId(), log.getActorUsername(), log.getActorRole(),
                log.getAction(), log.getDetails(), log.getDeviceId(), log.getTimestamp());
    }

    private String clean(String value, int max) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.length() <= max ? stripped : stripped.substring(0, max);
    }
}
