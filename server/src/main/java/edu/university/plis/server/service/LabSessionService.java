package edu.university.plis.server.service;

import edu.university.plis.server.domain.LabSession;
import edu.university.plis.server.domain.StudentProfile;
import edu.university.plis.server.domain.TeacherProfile;
import edu.university.plis.server.repository.EventLogRepository;
import edu.university.plis.server.repository.LabSessionRepository;
import edu.university.plis.server.repository.StudentProfileRepository;
import edu.university.plis.shared.dto.*;
import edu.university.plis.shared.model.LabSessionStatus;
import edu.university.plis.shared.model.NetworkConnectionType;
import edu.university.plis.shared.model.SessionType;
import edu.university.plis.shared.model.StudentEventType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class LabSessionService {
    private final LabSessionRepository labSessionRepository;
    private final StudentProfileRepository studentRepository;
    private final EventLogRepository eventLogRepository;
    private final CurrentActorService currentActorService;
    private final EventRecordingService eventRecordingService;
    private final LabActivityTracker activityTracker;
    private final LabCodeService labCodeService;

    public LabSessionService(
            LabSessionRepository labSessionRepository,
            StudentProfileRepository studentRepository,
            EventLogRepository eventLogRepository,
            CurrentActorService currentActorService,
            EventRecordingService eventRecordingService,
            LabActivityTracker activityTracker,
            LabCodeService labCodeService) {
        this.labSessionRepository = labSessionRepository;
        this.studentRepository = studentRepository;
        this.eventLogRepository = eventLogRepository;
        this.currentActorService = currentActorService;
        this.eventRecordingService = eventRecordingService;
        this.activityTracker = activityTracker;
        this.labCodeService = labCodeService;
    }

    @Transactional
    public LabSessionSummary create(String username, CreateLabSessionRequest request) {
        String courseCode = requiredText(request.courseCode(), "Course code", 40);
        String section = requiredText(request.section(), "Section", 40);
        Set<StudentProfile> students = resolveStudents(request.assignedStudentIds());
        int maxParticipants = request.maxParticipants() <= 0 ? students.size() : request.maxParticipants();
        if (maxParticipants > students.size()) {
            throw new InvalidRequestException("The participant limit cannot exceed the assigned student count");
        }
        NetworkConnectionType connectionPolicy = validatedPolicy(request.connectionPolicy());
        TeacherProfile teacher = currentActorService.requireTeachingProfile(username);
        return ViewMapper.toLabSummary(labSessionRepository.save(
                new LabSession(courseCode, section, teacher, students, maxParticipants, connectionPolicy)));
    }

    @Transactional(readOnly = true)
    public List<LabSessionSummary> listForTeacher(String username) {
        var actor = currentActorService.requireUser(username);
        List<LabSession> sessions = actor.getRole() == edu.university.plis.shared.model.RoleName.ROLE_ADMIN
                ? labSessionRepository.findAllByOrderByIdDesc()
                : labSessionRepository.findByTeacherUserUsernameIgnoreCaseOrderByIdDesc(username);
        return sessions.stream()
                .map(ViewMapper::toLabSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LabSessionSummary> listAvailableForStudent(String username) {
        StudentProfile student = currentActorService.requireStudent(username);
        return labSessionRepository.findDistinctByAssignedStudentsIdAndStatusInOrderByIdDesc(
                        student.getId(), EnumSet.of(LabSessionStatus.DRAFT, LabSessionStatus.ACTIVE)).stream()
                .map(ViewMapper::toLabSummary)
                .toList();
    }

    @Transactional
    public LabSessionSummary start(String username, long labSessionId) {
        LabSession session = requireSession(labSessionId);
        currentActorService.requireCanManage(username, session.getTeacher());
        session.start(Instant.now());
        return ViewMapper.toLabSummary(session);
    }

    @Transactional
    public LabSessionSummary end(String username, long labSessionId) {
        LabSession session = requireSession(labSessionId);
        currentActorService.requireCanManage(username, session.getTeacher());
        session.end(Instant.now());
        activityTracker.clear(labSessionId);
        return ViewMapper.toLabSummary(session);
    }

    @Transactional
    public void join(String username, long labSessionId, JoinLabRequest request) {
        LabSession session = labSessionRepository.findByIdForUpdate(labSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Lab session not found"));
        StudentProfile student = currentActorService.requireStudent(username);
        requireActiveAssignment(session, student);
        NetworkConnectionType actualConnection = request == null || request.connectionType() == null
                ? NetworkConnectionType.UNKNOWN : request.connectionType();
        requireAllowedConnection(session.getConnectionPolicy(), actualConnection);
        boolean newlyJoined = session.join(student);
        labCodeService.ensureDocument(session, student);
        if (newlyJoined) {
            eventRecordingService.recordLabEvent(labSessionId, student.getId(), StudentEventType.JOINED,
                    actualConnection.name());
        }
    }

    @Transactional
    public LabSessionSummary updatePolicy(
            String username, long labSessionId, UpdateLabPolicyRequest request) {
        if (request == null) {
            throw new InvalidRequestException("Lab policy is required");
        }
        LabSession session = labSessionRepository.findByIdForUpdate(labSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Lab session not found"));
        currentActorService.requireCanManage(username, session.getTeacher());
        session.updatePolicy(request.maxParticipants(), validatedPolicy(request.connectionPolicy()));
        return ViewMapper.toLabSummary(session);
    }

    @Transactional(readOnly = true)
    public void recordEvent(String username, long labSessionId, LabEventRequest request) {
        if (request == null || request.eventType() == null) {
            throw new InvalidRequestException("Event type is required");
        }
        if (!EnumSet.of(StudentEventType.FILE_SAVE, StudentEventType.COMPILE_START,
                StudentEventType.COMPILE_SUCCESS, StudentEventType.COMPILE_ERROR,
                StudentEventType.RUN_START, StudentEventType.RUN_END, StudentEventType.DISCONNECTED,
                StudentEventType.BROWSER_OPEN, StudentEventType.BROWSER_CLOSED,
                StudentEventType.AI_TOOL_OPEN, StudentEventType.AI_TOOL_CLOSED,
                StudentEventType.NETWORK_CHANGED)
                .contains(request.eventType())) {
            throw new InvalidRequestException("That event type is not valid in a lab session");
        }
        StudentProfile student = requireAssignedActiveStudent(username, labSessionId);
        eventRecordingService.recordLabEvent(labSessionId, student.getId(), request.eventType(), request.payload());
    }

    @Transactional(readOnly = true)
    public List<StudentActivitySnapshot> activity(String username, long labSessionId) {
        LabSession session = requireSession(labSessionId);
        currentActorService.requireCanManage(username, session.getTeacher());
        return activityTracker.snapshotsFor(session);
    }

    @Transactional(readOnly = true)
    public List<EventLogView> history(String username, long labSessionId, long studentId) {
        LabSession session = requireSession(labSessionId);
        currentActorService.requireCanManage(username, session.getTeacher());
        if (session.getAssignedStudents().stream().noneMatch(student -> student.getId() == studentId)) {
            throw new ResourceNotFoundException("Student is not assigned to this lab session");
        }
        return eventLogRepository.findBySessionTypeAndSessionIdAndStudentIdOrderByTimestampDesc(
                        SessionType.LAB, labSessionId, studentId).stream()
                .map(ViewMapper::toEventView)
                .toList();
    }

    private StudentProfile requireAssignedActiveStudent(String username, long labSessionId) {
        LabSession session = requireSession(labSessionId);
        StudentProfile student = currentActorService.requireStudent(username);
        requireActiveAssignment(session, student);
        if (!session.isJoined(student)) {
            throw new org.springframework.security.access.AccessDeniedException("Join this lab session first");
        }
        return student;
    }

    private void requireActiveAssignment(LabSession session, StudentProfile student) {
        if (session.getStatus() != LabSessionStatus.ACTIVE) {
            throw new ConflictException("The lab session is not active");
        }
        if (!session.isAssigned(student)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You are not assigned to this lab session");
        }
    }

    private NetworkConnectionType validatedPolicy(NetworkConnectionType policy) {
        NetworkConnectionType resolved = policy == null ? NetworkConnectionType.ANY : policy;
        if (resolved == NetworkConnectionType.UNKNOWN) {
            throw new InvalidRequestException("Unknown cannot be used as the teacher connection policy");
        }
        return resolved;
    }

    private void requireAllowedConnection(
            NetworkConnectionType policy, NetworkConnectionType actualConnection) {
        if (policy != NetworkConnectionType.ANY && policy != actualConnection) {
            throw new ConflictException("This class allows " + policy
                    + ", but the student client is connected using " + actualConnection);
        }
    }

    private LabSession requireSession(long id) {
        return labSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lab session not found"));
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
