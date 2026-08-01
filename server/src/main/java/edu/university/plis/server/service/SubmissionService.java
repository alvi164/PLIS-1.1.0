package edu.university.plis.server.service;

import edu.university.plis.server.domain.CodeSubmission;
import edu.university.plis.server.domain.ExamSession;
import edu.university.plis.server.domain.StudentProfile;
import edu.university.plis.server.repository.CodeSubmissionRepository;
import edu.university.plis.shared.dto.CodeSubmissionRequest;
import edu.university.plis.shared.dto.SubmissionReceipt;
import edu.university.plis.shared.model.StudentEventType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class SubmissionService {
    private final CodeSubmissionRepository submissionRepository;
    private final CurrentActorService currentActorService;
    private final ExamSessionService examSessionService;
    private final EventRecordingService eventRecordingService;
    private final IntegrityAnalysisService integrityAnalysisService;
    private final ExamCodeService examCodeService;

    public SubmissionService(
            CodeSubmissionRepository submissionRepository,
            CurrentActorService currentActorService,
            ExamSessionService examSessionService,
            EventRecordingService eventRecordingService,
            IntegrityAnalysisService integrityAnalysisService,
            ExamCodeService examCodeService) {
        this.submissionRepository = submissionRepository;
        this.currentActorService = currentActorService;
        this.examSessionService = examSessionService;
        this.eventRecordingService = eventRecordingService;
        this.integrityAnalysisService = integrityAnalysisService;
        this.examCodeService = examCodeService;
    }

    @Transactional
    public SubmissionReceipt submit(String username, long examSessionId, CodeSubmissionRequest request) {
        if (request == null || request.codeText() == null || request.codeText().isBlank()) {
            throw new InvalidRequestException("Code cannot be empty");
        }
        if (request.codeText().length() > 250_000) {
            throw new InvalidRequestException("Code exceeds the 250,000 character limit");
        }
        ExamSession exam = examSessionService.requireActiveAssignedExam(username, examSessionId);
        StudentProfile student = currentActorService.requireStudent(username);
        if (submissionRepository.existsByExamSessionIdAndStudentId(examSessionId, student.getId())) {
            throw new ConflictException("A submission has already been accepted for this exam");
        }
        Instant submittedAt = Instant.now();
        var finalCode = examCodeService.finalizeForSubmission(username, examSessionId, request, submittedAt);
        CodeSubmission submission = submissionRepository.saveAndFlush(new CodeSubmission(
                exam, student, finalCode.codeText(), finalCode.language(),
                finalCode.editingStartedAt(), submittedAt));
        eventRecordingService.recordExamEvent(
                examSessionId, student.getId(), StudentEventType.SUBMITTED, null);
        integrityAnalysisService.analyzeSubmission(examSessionId, submission.getId());
        return new SubmissionReceipt(submission.getId(), submittedAt, true);
    }
}
