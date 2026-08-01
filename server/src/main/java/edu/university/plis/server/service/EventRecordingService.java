package edu.university.plis.server.service;

import edu.university.plis.server.domain.EventLog;
import edu.university.plis.server.domain.ExamSession;
import edu.university.plis.server.domain.LabSession;
import edu.university.plis.server.domain.StudentProfile;
import edu.university.plis.server.repository.EventLogRepository;
import edu.university.plis.server.repository.ExamSessionRepository;
import edu.university.plis.server.repository.LabSessionRepository;
import edu.university.plis.server.repository.StudentProfileRepository;
import edu.university.plis.shared.dto.StudentActivitySnapshot;
import edu.university.plis.shared.dto.ExamParticipantSnapshot;
import edu.university.plis.shared.model.SessionType;
import edu.university.plis.shared.model.StudentEventType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Service
public class EventRecordingService {
    private final EventLogRepository eventLogRepository;
    private final LabSessionRepository labSessionRepository;
    private final ExamSessionRepository examSessionRepository;
    private final StudentProfileRepository studentRepository;
    private final LabActivityTracker labActivityTracker;
    private final SimpMessagingTemplate messagingTemplate;
    private final IntegrityAnalysisService integrityAnalysisService;
    private final ExamMonitoringService examMonitoringService;

    public EventRecordingService(
            EventLogRepository eventLogRepository,
            LabSessionRepository labSessionRepository,
            ExamSessionRepository examSessionRepository,
            StudentProfileRepository studentRepository,
            LabActivityTracker labActivityTracker,
            SimpMessagingTemplate messagingTemplate,
            IntegrityAnalysisService integrityAnalysisService,
            ExamMonitoringService examMonitoringService) {
        this.eventLogRepository = eventLogRepository;
        this.labSessionRepository = labSessionRepository;
        this.examSessionRepository = examSessionRepository;
        this.studentRepository = studentRepository;
        this.labActivityTracker = labActivityTracker;
        this.messagingTemplate = messagingTemplate;
        this.integrityAnalysisService = integrityAnalysisService;
        this.examMonitoringService = examMonitoringService;
    }

    @Async("eventExecutor")
    @Transactional
    public CompletableFuture<Void> recordExamEvent(
            long examSessionId, long studentId, StudentEventType eventType, String payload) {
        ExamSession exam = examSessionRepository.findById(examSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam session not found"));
        StudentProfile student = studentRepository.findByIdForEventUpdate(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));
        if (!exam.isAssigned(student)) {
            return CompletableFuture.completedFuture(null);
        }
        EventLog event = eventLogRepository.save(new EventLog(SessionType.EXAM, examSessionId, student,
                eventType, sanitizedPayload(payload), Instant.now()));
        eventLogRepository.flush();
        integrityAnalysisService.analyzeSignal(exam, student, eventType);
        ExamParticipantSnapshot snapshot = examMonitoringService.snapshot(exam, student);
        messagingTemplate.convertAndSend("/topic/exams/" + examSessionId, snapshot);
        return CompletableFuture.completedFuture(null);
    }

    @Async("eventExecutor")
    @Transactional
    public CompletableFuture<Void> recordLabEvent(
            long labSessionId, long studentId, StudentEventType eventType, String payload) {
        LabSession session = labSessionRepository.findById(labSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Lab session not found"));
        StudentProfile student = studentRepository.findByIdForEventUpdate(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));
        if (!session.isAssigned(student)) {
            return CompletableFuture.completedFuture(null);
        }
        Instant occurredAt = Instant.now();
        eventLogRepository.save(new EventLog(SessionType.LAB, labSessionId, student,
                eventType, sanitizedPayload(payload), occurredAt));
        StudentActivitySnapshot snapshot = labActivityTracker.record(
                labSessionId, student, eventType, payload, occurredAt);
        messagingTemplate.convertAndSend("/topic/labs/" + labSessionId, snapshot);
        return CompletableFuture.completedFuture(null);
    }

    static String sanitizedPayload(String payload) {
        if (payload == null) {
            return null;
        }
        String stripped = payload.strip();
        return stripped.length() <= 4_000 ? stripped : stripped.substring(0, 4_000);
    }
}
