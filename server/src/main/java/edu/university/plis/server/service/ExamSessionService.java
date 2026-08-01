package edu.university.plis.server.service;

import edu.university.plis.server.domain.AppUser;
import edu.university.plis.server.domain.ExamSession;
import edu.university.plis.server.domain.StudentProfile;
import edu.university.plis.server.domain.TeacherProfile;
import edu.university.plis.server.repository.ExamSessionRepository;
import edu.university.plis.server.repository.IntegrityFlagRepository;
import edu.university.plis.server.repository.StudentProfileRepository;
import edu.university.plis.shared.dto.*;
import edu.university.plis.shared.model.ExamSessionStatus;
import edu.university.plis.shared.model.RoleName;
import edu.university.plis.shared.model.StudentEventType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ExamSessionService {
    private final ExamSessionRepository examSessionRepository;
    private final StudentProfileRepository studentRepository;
    private final IntegrityFlagRepository integrityFlagRepository;
    private final CurrentActorService currentActorService;
    private final EventRecordingService eventRecordingService;
    private final ExamMonitoringService examMonitoringService;

    public ExamSessionService(
            ExamSessionRepository examSessionRepository,
            StudentProfileRepository studentRepository,
            IntegrityFlagRepository integrityFlagRepository,
            CurrentActorService currentActorService,
            EventRecordingService eventRecordingService,
            ExamMonitoringService examMonitoringService) {
        this.examSessionRepository = examSessionRepository;
        this.studentRepository = studentRepository;
        this.integrityFlagRepository = integrityFlagRepository;
        this.currentActorService = currentActorService;
        this.eventRecordingService = eventRecordingService;
        this.examMonitoringService = examMonitoringService;
    }

    @Transactional
    public ExamSummary create(String username, CreateExamRequest request) {
        if (request == null) {
            throw new InvalidRequestException("Exam details are required");
        }
        String title = requiredText(request.examTitle(), "Exam title", 160);
        String courseCode = requiredText(request.courseCode(), "Course code", 40);
        String question = requiredText(request.questionText(), "Question text", 100_000);
        if (request.scheduledStartTime() == null) {
            throw new InvalidRequestException("Scheduled start time is required");
        }
        if (request.durationMinutes() < 1 || request.durationMinutes() > 480) {
            throw new InvalidRequestException("Duration must be between 1 and 480 minutes");
        }
        Set<StudentProfile> students = resolveStudents(request.assignedStudentIds());
        TeacherProfile teacher = currentActorService.requireTeachingProfile(username);
        ExamSession exam = new ExamSession(title, courseCode, request.scheduledStartTime(),
                request.durationMinutes(), question, teacher, students);
        return ViewMapper.toExamSummary(examSessionRepository.save(exam));
    }

    @Transactional(readOnly = true)
    public List<ExamSummary> listForTeacher(String username) {
        AppUser actor = currentActorService.requireUser(username);
        List<ExamSession> exams = actor.getRole() == RoleName.ROLE_ADMIN
                ? examSessionRepository.findAllByOrderByScheduledStartTimeDesc()
                : examSessionRepository.findByCreatedByUserUsernameIgnoreCaseOrderByScheduledStartTimeDesc(username);
        return exams.stream()
                .map(ViewMapper::toExamSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExamSummary> listAvailableForStudent(String username) {
        StudentProfile student = currentActorService.requireStudent(username);
        return examSessionRepository.findDistinctByAssignedStudentsIdAndStatusInOrderByScheduledStartTimeAsc(
                        student.getId(), EnumSet.of(ExamSessionStatus.SCHEDULED, ExamSessionStatus.ACTIVE)).stream()
                .map(ViewMapper::toExamSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExamDetails details(String username, long examSessionId) {
        ExamSession exam = requireExam(examSessionId);
        AppUser actor = currentActorService.requireUser(username);
        if (actor.getRole() == RoleName.ROLE_STUDENT) {
            StudentProfile student = currentActorService.requireStudent(username);
            if (!exam.isAssigned(student)) {
                throw new org.springframework.security.access.AccessDeniedException("You are not assigned to this exam");
            }
            if (exam.getStatus() != ExamSessionStatus.ACTIVE) {
                throw new ConflictException("Questions are available only while the exam is active");
            }
        } else {
            currentActorService.requireCanManage(username, exam.getCreatedBy());
        }
        return ViewMapper.toExamDetails(exam);
    }

    @Transactional
    public ExamSummary start(String username, long examSessionId) {
        ExamSession exam = requireExam(examSessionId);
        currentActorService.requireCanManage(username, exam.getCreatedBy());
        exam.start(Instant.now());
        return ViewMapper.toExamSummary(exam);
    }

    @Transactional
    public ExamSummary end(String username, long examSessionId) {
        ExamSession exam = requireExam(examSessionId);
        currentActorService.requireCanManage(username, exam.getCreatedBy());
        exam.end(Instant.now());
        return ViewMapper.toExamSummary(exam);
    }

    @Transactional(readOnly = true)
    public void recordSignal(String username, long examSessionId, ExamSignalRequest request) {
        if (request == null || request.signalType() == null
                || !EnumSet.of(StudentEventType.JOINED, StudentEventType.FOCUS_LOST,
                StudentEventType.DISCONNECTED).contains(request.signalType())) {
            throw new InvalidRequestException("Signal must be JOINED, FOCUS_LOST, or DISCONNECTED");
        }
        ExamSession exam = requireExam(examSessionId);
        StudentProfile student = currentActorService.requireStudent(username);
        requireActiveAssignment(exam, student);
        eventRecordingService.recordExamEvent(
                examSessionId, student.getId(), request.signalType(), request.detail());
    }

    @Transactional(readOnly = true)
    public List<ExamParticipantSnapshot> dashboard(String username, long examSessionId) {
        ExamSession exam = requireExam(examSessionId);
        currentActorService.requireCanManage(username, exam.getCreatedBy());
        return examMonitoringService.dashboard(exam);
    }

    @Transactional(readOnly = true)
    public List<IntegrityFlagView> integrityReport(String username, long examSessionId) {
        ExamSession exam = requireExam(examSessionId);
        currentActorService.requireCanManage(username, exam.getCreatedBy());
        return integrityFlagRepository.findByExamSessionIdOrderByTimestampAsc(examSessionId).stream()
                .map(ViewMapper::toIntegrityFlagView)
                .toList();
    }

    ExamSession requireActiveAssignedExam(String username, long examSessionId) {
        ExamSession exam = requireExam(examSessionId);
        StudentProfile student = currentActorService.requireStudent(username);
        requireActiveAssignment(exam, student);
        return exam;
    }

    private void requireActiveAssignment(ExamSession exam, StudentProfile student) {
        if (!exam.isAssigned(student)) {
            throw new org.springframework.security.access.AccessDeniedException("You are not assigned to this exam");
        }
        if (exam.getStatus() != ExamSessionStatus.ACTIVE) {
            throw new ConflictException("The exam is not active");
        }
        Instant deadline = exam.getActualStartTime().plusSeconds(exam.getDurationMinutes() * 60L);
        if (Instant.now().isAfter(deadline)) {
            throw new ConflictException("The exam submission window has closed");
        }
    }

    private ExamSession requireExam(long id) {
        return examSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exam session not found"));
    }

    private Set<StudentProfile> resolveStudents(Set<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            throw new InvalidRequestException("Assign at least one student");
        }
        List<StudentProfile> students = studentRepository.findAllById(studentIds);
        if (students.size() != studentIds.size()) {
            throw new InvalidRequestException("One or more assigned students do not exist");
        }
        return new LinkedHashSet<>(students);
    }

    private String requiredText(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(label + " is required");
        }
        String stripped = value.strip();
        if (stripped.length() > maxLength) {
            throw new InvalidRequestException(label + " must be at most " + maxLength + " characters");
        }
        return stripped;
    }
}
