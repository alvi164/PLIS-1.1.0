package edu.university.plis.server.service;

import edu.university.plis.server.domain.*;
import edu.university.plis.server.repository.*;
import edu.university.plis.shared.dto.CodeSubmissionRequest;
import edu.university.plis.shared.dto.ExamCodeSnapshot;
import edu.university.plis.shared.dto.ExamCodeUpdateRequest;
import edu.university.plis.shared.model.ProgrammingLanguage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ExamCodeService {
    private static final int MAX_CODE_LENGTH = 250_000;

    private final ExamCodeDocumentRepository documentRepository;
    private final ExamCodeRevisionRepository revisionRepository;
    private final CodeSubmissionRepository submissionRepository;
    private final ExamSessionRepository examSessionRepository;
    private final CurrentActorService currentActorService;
    private final ExamSessionService examSessionService;

    public ExamCodeService(
            ExamCodeDocumentRepository documentRepository,
            ExamCodeRevisionRepository revisionRepository,
            CodeSubmissionRepository submissionRepository,
            ExamSessionRepository examSessionRepository,
            CurrentActorService currentActorService,
            ExamSessionService examSessionService) {
        this.documentRepository = documentRepository;
        this.revisionRepository = revisionRepository;
        this.submissionRepository = submissionRepository;
        this.examSessionRepository = examSessionRepository;
        this.currentActorService = currentActorService;
        this.examSessionService = examSessionService;
    }

    @Transactional
    public ExamCodeSnapshot readForStudent(String username, long examSessionId) {
        ExamSession exam = examSessionService.requireActiveAssignedExam(username, examSessionId);
        StudentProfile student = currentActorService.requireStudent(username);
        ExamCodeDocument document = documentRepository.findByExamSessionIdAndStudentId(
                        examSessionId, student.getId())
                .orElseGet(() -> documentRepository.save(
                        new ExamCodeDocument(exam, student, Instant.now())));
        return snapshot(document);
    }

    @Transactional
    public ExamCodeSnapshot updateForStudent(
            String username, long examSessionId, ExamCodeUpdateRequest request) {
        ExamSession exam = examSessionService.requireActiveAssignedExam(username, examSessionId);
        StudentProfile student = currentActorService.requireStudent(username);
        ValidatedCode validated = validate(request == null ? null : request.language(),
                request == null ? null : request.codeText());
        ExamCodeDocument document = documentRepository.findForUpdate(examSessionId, student.getId())
                .orElseGet(() -> documentRepository.save(
                        new ExamCodeDocument(exam, student, Instant.now())));
        if (request.baseRevision() != document.getRevision()) {
            throw new ConflictException("The exam code changed on another computer. Reload before saving");
        }
        requireEditable(document, validated.language());
        if (!validated.code().equals(document.getCodeText()) || document.getLanguage() == null) {
            document.update(validated.language(), validated.code(), request.editingStartedAt(), Instant.now());
            revisionRepository.save(new ExamCodeRevision(document));
        }
        return snapshot(document);
    }

    @Transactional(readOnly = true)
    public ExamCodeSnapshot readForTeacher(String username, long examSessionId, long studentId) {
        ExamSession exam = examSessionRepository.findById(examSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam session not found"));
        currentActorService.requireCanManage(username, exam.getCreatedBy());
        StudentProfile student = assignedStudent(exam, studentId);
        return documentRepository.findByExamSessionIdAndStudentId(examSessionId, studentId)
                .map(this::snapshot)
                .orElseGet(() -> submissionRepository.findByExamSessionIdAndStudentId(examSessionId, studentId)
                        .map(this::legacySubmissionSnapshot)
                        .orElseGet(() -> emptySnapshot(exam, student)));
    }

    @Transactional
    public ExamCodeSnapshot finalizeForSubmission(
            String username, long examSessionId, CodeSubmissionRequest request, Instant submittedAt) {
        ExamSession exam = examSessionService.requireActiveAssignedExam(username, examSessionId);
        StudentProfile student = currentActorService.requireStudent(username);
        ValidatedCode validated = validate(request == null ? null : request.language(),
                request == null ? null : request.codeText());
        if (validated.code().isBlank()) {
            throw new InvalidRequestException("Code cannot be empty");
        }
        ExamCodeDocument document = documentRepository.findForUpdate(examSessionId, student.getId())
                .orElseGet(() -> documentRepository.save(
                        new ExamCodeDocument(exam, student, Instant.now())));
        requireEditable(document, validated.language());
        if (!validated.code().equals(document.getCodeText()) || document.getLanguage() == null) {
            document.update(validated.language(), validated.code(), request.editingStartedAt(), submittedAt);
            revisionRepository.save(new ExamCodeRevision(document));
        }
        document.markSubmitted(submittedAt);
        return snapshot(document);
    }

    private void requireEditable(ExamCodeDocument document, ProgrammingLanguage language) {
        if (document.getSubmittedAt() != null) {
            throw new ConflictException("This exam code has already been submitted");
        }
        if (document.getLanguage() != null && document.getLanguage() != language) {
            throw new ConflictException("Programming language cannot be changed after coding has started");
        }
    }

    private ValidatedCode validate(ProgrammingLanguage language, String code) {
        if (language == null) {
            throw new InvalidRequestException("Choose a programming language before coding");
        }
        if (code == null) {
            throw new InvalidRequestException("Code text is required");
        }
        if (code.length() > MAX_CODE_LENGTH) {
            throw new InvalidRequestException("Code exceeds the 250,000 character limit");
        }
        return new ValidatedCode(language, code);
    }

    private StudentProfile assignedStudent(ExamSession exam, long studentId) {
        return exam.getAssignedStudents().stream()
                .filter(student -> student.getId() == studentId)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Student is not assigned to this exam"));
    }

    private ExamCodeSnapshot snapshot(ExamCodeDocument document) {
        ProgrammingLanguage language = document.getLanguage();
        return new ExamCodeSnapshot(document.getExamSession().getId(), document.getStudent().getId(),
                document.getStudent().getStudentNumber(), document.getStudent().getUser().getDisplayName(),
                language, language == null ? null : language.defaultFileName(), document.getCodeText(),
                document.getRevision(), document.getEditingStartedAt(), document.getModifiedAt(),
                document.getSubmittedAt() != null, document.getSubmittedAt());
    }

    private ExamCodeSnapshot legacySubmissionSnapshot(CodeSubmission submission) {
        ProgrammingLanguage language = submission.getLanguage();
        return new ExamCodeSnapshot(submission.getExamSession().getId(), submission.getStudent().getId(),
                submission.getStudent().getStudentNumber(), submission.getStudent().getUser().getDisplayName(),
                language, language.defaultFileName(), submission.getCodeText(), 0,
                submission.getEditingStartedAt(), submission.getSubmittedAt(), true, submission.getSubmittedAt());
    }

    private ExamCodeSnapshot emptySnapshot(ExamSession exam, StudentProfile student) {
        return new ExamCodeSnapshot(exam.getId(), student.getId(), student.getStudentNumber(),
                student.getUser().getDisplayName(), null, null, "", 0, null, null, false, null);
    }

    private record ValidatedCode(ProgrammingLanguage language, String code) { }
}
