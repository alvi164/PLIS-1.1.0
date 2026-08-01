package edu.university.plis.server.service;

import edu.university.plis.server.domain.*;
import edu.university.plis.server.repository.CodeSubmissionRepository;
import edu.university.plis.server.repository.EventLogRepository;
import edu.university.plis.server.repository.ExamSessionRepository;
import edu.university.plis.server.repository.IntegrityFlagRepository;
import edu.university.plis.shared.model.IntegrityFlagType;
import edu.university.plis.shared.model.SessionType;
import edu.university.plis.shared.model.StudentEventType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class IntegrityAnalysisService {
    private final EventLogRepository eventLogRepository;
    private final CodeSubmissionRepository submissionRepository;
    private final IntegrityFlagRepository flagRepository;
    private final ExamSessionRepository examSessionRepository;
    private final CodeSimilarityCalculator similarityCalculator;

    public IntegrityAnalysisService(
            EventLogRepository eventLogRepository,
            CodeSubmissionRepository submissionRepository,
            IntegrityFlagRepository flagRepository,
            ExamSessionRepository examSessionRepository,
            CodeSimilarityCalculator similarityCalculator) {
        this.eventLogRepository = eventLogRepository;
        this.submissionRepository = submissionRepository;
        this.flagRepository = flagRepository;
        this.examSessionRepository = examSessionRepository;
        this.similarityCalculator = similarityCalculator;
    }

    @Transactional
    public void analyzeSignal(ExamSession exam, StudentProfile student, StudentEventType signalType) {
        if (signalType == StudentEventType.DISCONNECTED) {
            long count = countSignals(exam, student, signalType);
            if (count >= IntegrityRules.DISCONNECTION_THRESHOLD) {
                addFlagIfAbsent(exam, student, IntegrityFlagType.REPEATED_DISCONNECTIONS,
                        "Disconnected " + count + " times during the exam");
            }
        } else if (signalType == StudentEventType.FOCUS_LOST) {
            long count = countSignals(exam, student, signalType);
            if (count >= IntegrityRules.FOCUS_LOSS_THRESHOLD) {
                addFlagIfAbsent(exam, student, IntegrityFlagType.EXCESSIVE_FOCUS_LOSS,
                        "Left the exam window " + count + " times");
            }
        }
    }

    @Async("eventExecutor")
    @Transactional
    public void analyzeSubmission(long examSessionId, long submissionId) {
        ExamSession exam = examSessionRepository.findById(examSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam session not found"));
        List<CodeSubmission> submissions = submissionRepository.findByExamSessionIdOrderBySubmittedAtAsc(examSessionId);
        CodeSubmission submitted = submissions.stream()
                .filter(candidate -> candidate.getId() == submissionId)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found"));
        analyzeCompletionTimes(exam, submissions);
        analyzeSimilarity(exam, submitted, submissions);
    }

    private void analyzeCompletionTimes(ExamSession exam, List<CodeSubmission> submissions) {
        if (exam.getActualStartTime() == null) {
            return;
        }
        for (CodeSubmission candidate : submissions) {
            Duration candidateDuration = Duration.between(exam.getActualStartTime(), candidate.getSubmittedAt());
            List<Duration> peerDurations = submissions.stream()
                    .filter(peer -> !peer.getId().equals(candidate.getId()))
                    .map(peer -> Duration.between(exam.getActualStartTime(), peer.getSubmittedAt()))
                    .toList();
            if (IntegrityRules.isFastCompletionOutlier(candidateDuration, peerDurations)) {
                addFlagIfAbsent(exam, candidate.getStudent(), IntegrityFlagType.FAST_COMPLETION_OUTLIER,
                        "Submitted in " + Math.max(0, candidateDuration.toMinutes())
                                + " minutes, less than half the peer average");
            }
        }
    }

    private void analyzeSimilarity(
            ExamSession exam, CodeSubmission submitted, List<CodeSubmission> submissions) {
        for (CodeSubmission peer : submissions) {
            if (peer.getId().equals(submitted.getId())) {
                continue;
            }
            double similarity = similarityCalculator.calculate(submitted.getCodeText(), peer.getCodeText());
            if (similarity >= IntegrityRules.SIMILARITY_THRESHOLD) {
                int percentage = (int) Math.round(similarity * 100);
                addFlagIfAbsent(exam, submitted.getStudent(), IntegrityFlagType.HIGH_CODE_SIMILARITY,
                        percentage + "% token similarity with " + peer.getStudent().getStudentNumber());
                addFlagIfAbsent(exam, peer.getStudent(), IntegrityFlagType.HIGH_CODE_SIMILARITY,
                        percentage + "% token similarity with " + submitted.getStudent().getStudentNumber());
            }
        }
    }

    private long countSignals(ExamSession exam, StudentProfile student, StudentEventType type) {
        return eventLogRepository.countBySessionTypeAndSessionIdAndStudentIdAndEventType(
                SessionType.EXAM, exam.getId(), student.getId(), type);
    }

    private void addFlagIfAbsent(
            ExamSession exam,
            StudentProfile student,
            IntegrityFlagType type,
            String description) {
        if (!flagRepository.existsByExamSessionIdAndStudentIdAndFlagType(exam.getId(), student.getId(), type)) {
            flagRepository.save(new IntegrityFlag(exam, student, type, description, Instant.now()));
        }
    }
}
