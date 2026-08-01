package edu.university.plis.server.service;

import edu.university.plis.server.domain.EventLog;
import edu.university.plis.server.domain.ExamSession;
import edu.university.plis.server.domain.StudentProfile;
import edu.university.plis.server.repository.CodeSubmissionRepository;
import edu.university.plis.server.repository.EventLogRepository;
import edu.university.plis.server.repository.ExamCodeDocumentRepository;
import edu.university.plis.server.repository.IntegrityFlagRepository;
import edu.university.plis.shared.dto.ExamParticipantSnapshot;
import edu.university.plis.shared.model.ExamParticipantState;
import edu.university.plis.shared.model.SessionType;
import edu.university.plis.shared.model.StudentEventType;
import edu.university.plis.shared.model.ProgrammingLanguage;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class ExamMonitoringService {
    private final EventLogRepository eventLogRepository;
    private final CodeSubmissionRepository submissionRepository;
    private final IntegrityFlagRepository flagRepository;
    private final ExamCodeDocumentRepository examCodeRepository;

    public ExamMonitoringService(
            EventLogRepository eventLogRepository,
            CodeSubmissionRepository submissionRepository,
            IntegrityFlagRepository flagRepository,
            ExamCodeDocumentRepository examCodeRepository) {
        this.eventLogRepository = eventLogRepository;
        this.submissionRepository = submissionRepository;
        this.flagRepository = flagRepository;
        this.examCodeRepository = examCodeRepository;
    }

    public List<ExamParticipantSnapshot> dashboard(ExamSession exam) {
        return exam.getAssignedStudents().stream()
                .map(student -> snapshot(exam, student))
                .sorted(Comparator.comparing(ExamParticipantSnapshot::studentNumber))
                .toList();
    }

    public ExamParticipantSnapshot snapshot(ExamSession exam, StudentProfile student) {
        Optional<EventLog> latestEvent = eventLogRepository
                .findFirstBySessionTypeAndSessionIdAndStudentIdOrderByTimestampDesc(
                        SessionType.EXAM, exam.getId(), student.getId());
        boolean submitted = submissionRepository.existsByExamSessionIdAndStudentId(exam.getId(), student.getId());
        long disconnects = count(exam, student, StudentEventType.DISCONNECTED);
        long focusLosses = count(exam, student, StudentEventType.FOCUS_LOST);
        long flagCount = flagRepository.countByExamSessionIdAndStudentId(exam.getId(), student.getId());
        var liveCode = examCodeRepository.findByExamSessionIdAndStudentId(exam.getId(), student.getId());
        ProgrammingLanguage language = liveCode.map(edu.university.plis.server.domain.ExamCodeDocument::getLanguage)
                .orElseGet(() -> submissionRepository.findByExamSessionIdAndStudentId(exam.getId(), student.getId())
                        .map(edu.university.plis.server.domain.CodeSubmission::getLanguage).orElse(null));
        long codeRevision = liveCode.map(edu.university.plis.server.domain.ExamCodeDocument::getRevision).orElse(0L);
        return new ExamParticipantSnapshot(exam.getId(), student.getId(), student.getStudentNumber(),
                student.getUser().getDisplayName(), participantState(latestEvent, submitted),
                latestEvent.map(EventLog::getTimestamp).orElse(null), disconnects, focusLosses,
                Math.toIntExact(flagCount), language,
                language == null ? null : language.defaultFileName(), codeRevision);
    }

    private long count(ExamSession exam, StudentProfile student, StudentEventType eventType) {
        return eventLogRepository.countBySessionTypeAndSessionIdAndStudentIdAndEventType(
                SessionType.EXAM, exam.getId(), student.getId(), eventType);
    }

    private ExamParticipantState participantState(Optional<EventLog> latestEvent, boolean submitted) {
        if (submitted) {
            return ExamParticipantState.SUBMITTED;
        }
        if (latestEvent.isEmpty()) {
            return ExamParticipantState.NOT_CONNECTED;
        }
        return switch (latestEvent.get().getEventType()) {
            case DISCONNECTED -> ExamParticipantState.DISCONNECTED;
            case FOCUS_LOST -> ExamParticipantState.FOCUS_LOST;
            default -> ExamParticipantState.CONNECTED;
        };
    }
}
