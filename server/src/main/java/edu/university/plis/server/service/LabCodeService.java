package edu.university.plis.server.service;

import edu.university.plis.server.domain.*;
import edu.university.plis.server.repository.LabCodeDocumentRepository;
import edu.university.plis.server.repository.LabCodeRevisionRepository;
import edu.university.plis.server.repository.LabSessionRepository;
import edu.university.plis.shared.dto.LabCodeSnapshot;
import edu.university.plis.shared.dto.LabCodeUpdateRequest;
import edu.university.plis.shared.model.CodeAuthorType;
import edu.university.plis.shared.model.LabSessionStatus;
import edu.university.plis.shared.model.StudentEventType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class LabCodeService {
    private static final int MAX_CODE_LENGTH = 250_000;

    private final LabCodeDocumentRepository documentRepository;
    private final LabCodeRevisionRepository revisionRepository;
    private final LabSessionRepository labSessionRepository;
    private final CurrentActorService currentActorService;
    private final EventRecordingService eventRecordingService;

    public LabCodeService(
            LabCodeDocumentRepository documentRepository,
            LabCodeRevisionRepository revisionRepository,
            LabSessionRepository labSessionRepository,
            CurrentActorService currentActorService,
            EventRecordingService eventRecordingService) {
        this.documentRepository = documentRepository;
        this.revisionRepository = revisionRepository;
        this.labSessionRepository = labSessionRepository;
        this.currentActorService = currentActorService;
        this.eventRecordingService = eventRecordingService;
    }

    @Transactional
    public void ensureDocument(LabSession session, StudentProfile student) {
        if (documentRepository.findByLabSessionIdAndStudentId(session.getId(), student.getId()).isEmpty()) {
            documentRepository.save(new LabCodeDocument(
                    session, student, student.getUser().getDisplayName(), Instant.now()));
        }
    }

    @Transactional(readOnly = true)
    public LabCodeSnapshot readForStudent(String username, long labSessionId) {
        StudentProfile student = currentActorService.requireStudent(username);
        LabSession session = requireSession(labSessionId);
        requireActiveJoined(session, student);
        return toSnapshot(requireDocument(labSessionId, student.getId()));
    }

    @Transactional
    public LabCodeSnapshot updateForStudent(
            String username, long labSessionId, LabCodeUpdateRequest request) {
        StudentProfile student = currentActorService.requireStudent(username);
        LabSession session = requireSession(labSessionId);
        requireActiveJoined(session, student);
        return update(labSessionId, student, request, CodeAuthorType.STUDENT,
                student.getUser().getDisplayName(), StudentEventType.CODE_UPDATED);
    }

    @Transactional(readOnly = true)
    public LabCodeSnapshot readForTeacher(String username, long labSessionId, long studentId) {
        LabSession session = requireSession(labSessionId);
        currentActorService.requireCanManage(username, session.getTeacher());
        StudentProfile student = requireAssignedStudent(session, studentId);
        return toSnapshot(requireDocument(labSessionId, student.getId()));
    }

    @Transactional
    public LabCodeSnapshot updateForTeacher(
            String username, long labSessionId, long studentId, LabCodeUpdateRequest request) {
        LabSession session = requireSession(labSessionId);
        currentActorService.requireCanManage(username, session.getTeacher());
        StudentProfile student = requireAssignedStudent(session, studentId);
        requireActiveJoined(session, student);
        AppUser teacher = currentActorService.requireUser(username);
        return update(labSessionId, student, request, CodeAuthorType.TEACHER,
                teacher.getDisplayName(), StudentEventType.TEACHER_CODE_EDIT);
    }

    private LabCodeSnapshot update(
            long labSessionId,
            StudentProfile student,
            LabCodeUpdateRequest request,
            CodeAuthorType authorType,
            String authorName,
            StudentEventType eventType) {
        String code = validatedCode(request);
        LabCodeDocument document = documentRepository.findForUpdate(labSessionId, student.getId())
                .orElseThrow(() -> new ResourceNotFoundException("The student's lab code document was not found"));
        if (request.baseRevision() != document.getRevision()) {
            throw new ConflictException("The code changed on another computer. Refresh before saving again");
        }
        document.update(code, authorType, authorName, Instant.now());
        revisionRepository.save(new LabCodeRevision(document));
        eventRecordingService.recordLabEvent(labSessionId, student.getId(), eventType,
                "Code revision " + document.getRevision() + " by " + authorName);
        return toSnapshot(document);
    }

    private String validatedCode(LabCodeUpdateRequest request) {
        if (request == null || request.codeText() == null) {
            throw new InvalidRequestException("Code text is required");
        }
        if (request.baseRevision() < 0) {
            throw new InvalidRequestException("Code revision cannot be negative");
        }
        if (request.codeText().length() > MAX_CODE_LENGTH) {
            throw new InvalidRequestException("Code text is too large");
        }
        return request.codeText();
    }

    private void requireActiveJoined(LabSession session, StudentProfile student) {
        if (session.getStatus() != LabSessionStatus.ACTIVE) {
            throw new ConflictException("Code can be changed only while the class is active");
        }
        if (!session.isAssigned(student) || !session.isJoined(student)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "The student has not joined this lab session");
        }
    }

    private StudentProfile requireAssignedStudent(LabSession session, long studentId) {
        return session.getAssignedStudents().stream()
                .filter(student -> student.getId() == studentId)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Student is not assigned to this lab session"));
    }

    private LabSession requireSession(long labSessionId) {
        return labSessionRepository.findById(labSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Lab session not found"));
    }

    private LabCodeDocument requireDocument(long labSessionId, long studentId) {
        return documentRepository.findByLabSessionIdAndStudentId(labSessionId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("The student has not joined this lab yet"));
    }

    private LabCodeSnapshot toSnapshot(LabCodeDocument document) {
        return new LabCodeSnapshot(document.getLabSession().getId(), document.getStudent().getId(),
                document.getStudent().getUser().getDisplayName(), document.getCodeText(), document.getRevision(),
                document.getLastAuthorType(), document.getLastAuthorName(), document.getModifiedAt());
    }
}
