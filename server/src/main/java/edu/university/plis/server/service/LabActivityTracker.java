package edu.university.plis.server.service;

import edu.university.plis.server.domain.LabSession;
import edu.university.plis.server.domain.StudentProfile;
import edu.university.plis.shared.dto.StudentActivitySnapshot;
import edu.university.plis.shared.model.StudentActivityStatus;
import edu.university.plis.shared.model.StudentEventType;
import edu.university.plis.shared.model.NetworkConnectionType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LabActivityTracker {
    private static final Pattern CODE_REVISION = Pattern.compile("Code revision (\\d+)");
    private final ConcurrentMap<ActivityKey, StudentActivitySnapshot> currentActivity = new ConcurrentHashMap<>();

    public List<StudentActivitySnapshot> snapshotsFor(LabSession session) {
        return session.getAssignedStudents().stream()
                .map(student -> currentActivity.computeIfAbsent(
                        new ActivityKey(session.getId(), student.getId()),
                        ignored -> offlineSnapshot(session.getId(), student)))
                .sorted(Comparator.comparing(StudentActivitySnapshot::studentNumber))
                .toList();
    }

    public StudentActivitySnapshot record(
            long labSessionId,
            StudentProfile student,
            StudentEventType eventType,
            String payload,
            Instant occurredAt) {
        ActivityKey key = new ActivityKey(labSessionId, student.getId());
        return currentActivity.compute(key, (ignored, previous) -> {
            if (previous != null && previous.lastEventAt() != null
                    && previous.lastEventAt().isAfter(occurredAt)) {
                return previous;
            }
            NetworkConnectionType connectionType = connectionType(eventType, payload, previous);
            long codeRevision = codeRevision(eventType, payload, previous);
            return new StudentActivitySnapshot(labSessionId, student.getId(), student.getStudentNumber(),
                    student.getUser().getDisplayName(), mapStatus(eventType, previous), occurredAt,
                    detail(eventType, payload), connectionType, codeRevision);
        });
    }

    public void clear(long labSessionId) {
        currentActivity.keySet().removeIf(key -> key.labSessionId == labSessionId);
    }

    private StudentActivitySnapshot offlineSnapshot(long labSessionId, StudentProfile student) {
        return new StudentActivitySnapshot(labSessionId, student.getId(), student.getStudentNumber(),
                student.getUser().getDisplayName(), StudentActivityStatus.OFFLINE, null, null,
                NetworkConnectionType.UNKNOWN, 0);
    }

    private StudentActivityStatus mapStatus(
            StudentEventType eventType, StudentActivitySnapshot previous) {
        return switch (eventType) {
            case JOINED, RUN_END, COMPILE_SUCCESS -> StudentActivityStatus.IDLE;
            case FILE_SAVE, CODE_UPDATED, TEACHER_CODE_EDIT -> StudentActivityStatus.EDITING;
            case COMPILE_START -> StudentActivityStatus.COMPILING;
            case COMPILE_ERROR -> StudentActivityStatus.ERROR;
            case RUN_START -> StudentActivityStatus.RUNNING;
            case DISCONNECTED -> StudentActivityStatus.OFFLINE;
            default -> previous == null ? StudentActivityStatus.IDLE : previous.status();
        };
    }

    private NetworkConnectionType connectionType(
            StudentEventType eventType, String payload, StudentActivitySnapshot previous) {
        NetworkConnectionType fallback = previous == null || previous.connectionType() == null
                ? NetworkConnectionType.UNKNOWN : previous.connectionType();
        if (eventType != StudentEventType.JOINED && eventType != StudentEventType.NETWORK_CHANGED) {
            return fallback;
        }
        try {
            return NetworkConnectionType.valueOf(payload);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return fallback;
        }
    }

    private long codeRevision(
            StudentEventType eventType, String payload, StudentActivitySnapshot previous) {
        long fallback = previous == null ? 0 : previous.codeRevision();
        if (eventType != StudentEventType.CODE_UPDATED && eventType != StudentEventType.TEACHER_CODE_EDIT) {
            return fallback;
        }
        Matcher matcher = CODE_REVISION.matcher(payload == null ? "" : payload);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : fallback + 1;
    }

    private String detail(StudentEventType eventType, String payload) {
        return switch (eventType) {
            case COMPILE_ERROR -> firstLine(payload);
            case TEACHER_CODE_EDIT, BROWSER_OPEN, BROWSER_CLOSED,
                    AI_TOOL_OPEN, AI_TOOL_CLOSED, NETWORK_CHANGED -> firstLine(payload);
            default -> null;
        };
    }

    private String firstLine(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        String firstLine = payload.lines().findFirst().orElse(payload).strip();
        return firstLine.length() <= 240 ? firstLine : firstLine.substring(0, 240);
    }

    private record ActivityKey(long labSessionId, long studentId) {
    }
}
