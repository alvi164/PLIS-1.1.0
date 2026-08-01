package edu.university.plis.server.service;

import edu.university.plis.server.domain.AppUser;
import edu.university.plis.server.domain.StudentProfile;
import edu.university.plis.server.repository.AppUserRepository;
import edu.university.plis.server.repository.StudentConnectionRepository;
import edu.university.plis.server.repository.StudentProfileRepository;
import edu.university.plis.shared.dto.*;
import edu.university.plis.shared.model.DeviceConnectionStatus;
import edu.university.plis.shared.model.RoleName;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Service
public class StudentAccountService {
    public static final String DEFAULT_STUDENT_PASSWORD = "12345";

    private final AppUserRepository userRepository;
    private final StudentProfileRepository studentRepository;
    private final StudentConnectionRepository connectionRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentActorService currentActorService;
    private final ConnectionApprovalService connectionService;
    private final AuditService auditService;

    public StudentAccountService(
            AppUserRepository userRepository,
            StudentProfileRepository studentRepository,
            StudentConnectionRepository connectionRepository,
            PasswordEncoder passwordEncoder,
            CurrentActorService currentActorService,
            ConnectionApprovalService connectionService,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.connectionRepository = connectionRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentActorService = currentActorService;
        this.connectionService = connectionService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<StudentAccountView> list() {
        return studentRepository.findAllByOrderByStudentNumberAsc().stream().map(this::view).toList();
    }

    @Transactional
    public StudentAccountView create(String teacherUsername, CreateStudentAccountRequest request) {
        if (request == null) {
            throw new InvalidRequestException("Student account details are required");
        }
        String username = SetupService.username(request.username());
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("That login ID is already in use");
        }
        String displayName = SetupService.required(request.displayName(), "Student name", 120);
        String studentNumber = request.studentNumber() == null || request.studentNumber().isBlank()
                ? username : SetupService.required(request.studentNumber(), "Student number", 40);
        String password = request.initialPassword() == null || request.initialPassword().isBlank()
                ? DEFAULT_STUDENT_PASSWORD : SetupService.password(request.initialPassword());
        String program = request.program() == null || request.program().isBlank()
                ? null : SetupService.required(request.program(), "Program", 120);
        Integer semester = request.semester();
        if (semester != null && (semester < 1 || semester > 20)) {
            throw new InvalidRequestException("Semester must be between 1 and 20");
        }
        AppUser user = userRepository.save(new AppUser(username, passwordEncoder.encode(password),
                RoleName.ROLE_STUDENT, displayName));
        StudentProfile student = studentRepository.save(
                new StudentProfile(user, studentNumber, program, semester));
        auditService.record(teacherUsername, RoleName.ROLE_TEACHER, "STUDENT_ACCOUNT_CREATED",
                username + " / " + studentNumber, null);
        return view(student);
    }

    @Transactional
    public List<StudentAccountView> createBulk(String teacherUsername, BulkStudentAccountsRequest request) {
        if (request == null || request.accounts() == null || request.accounts().isEmpty()) {
            throw new InvalidRequestException("Add at least one student account");
        }
        if (request.accounts().size() > 500) {
            throw new InvalidRequestException("A bulk operation can create at most 500 student accounts");
        }
        Set<String> usernames = new HashSet<>();
        for (CreateStudentAccountRequest account : request.accounts()) {
            if (account == null) {
                throw new InvalidRequestException("Bulk account rows cannot be empty");
            }
            String username = SetupService.username(account.username());
            if (!usernames.add(username.toLowerCase(Locale.ROOT))) {
                throw new ConflictException("Duplicate login ID in bulk request: " + username);
            }
            if (userRepository.existsByUsernameIgnoreCase(username)) {
                throw new ConflictException("Login ID already exists: " + username);
            }
        }
        List<StudentAccountView> created = request.accounts().stream()
                .map(account -> create(teacherUsername, account))
                .toList();
        auditService.record(teacherUsername, RoleName.ROLE_TEACHER, "STUDENT_ACCOUNTS_BULK_CREATED",
                "Created " + created.size() + " student accounts", null);
        return created;
    }

    @Transactional
    public StudentAccountView resetPassword(
            String teacherUsername, long studentId, PasswordResetRequest request) {
        StudentProfile student = requireStudent(studentId);
        String password = SetupService.password(request == null ? null : request.newPassword());
        student.getUser().updatePasswordHash(passwordEncoder.encode(password));
        connectionService.terminateStudent(studentId, teacherUsername);
        auditService.record(teacherUsername, RoleName.ROLE_TEACHER, "STUDENT_PASSWORD_RESET",
                student.getUser().getUsername(), null);
        return view(student);
    }

    @Transactional
    public StudentAccountView setEnabled(
            String teacherUsername, long studentId, AccountEnabledRequest request) {
        StudentProfile student = requireStudent(studentId);
        boolean enabled = request != null && request.enabled();
        student.getUser().setEnabled(enabled);
        if (!enabled) {
            connectionService.terminateStudent(studentId, teacherUsername);
        }
        auditService.record(teacherUsername, RoleName.ROLE_TEACHER,
                enabled ? "STUDENT_ACCOUNT_ENABLED" : "STUDENT_ACCOUNT_DISABLED",
                student.getUser().getUsername(), null);
        return view(student);
    }

    @Transactional
    public void changeOwnPassword(String username, PasswordChangeRequest request) {
        if (request == null) {
            throw new InvalidRequestException("Password details are required");
        }
        AppUser user = currentActorService.requireUser(username);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidRequestException("Current password is incorrect");
        }
        String newPassword = SetupService.password(request.newPassword());
        user.updatePasswordHash(passwordEncoder.encode(newPassword));
        auditService.record(username, user.getRole(), "PASSWORD_CHANGED", "Self-service password change", null);
    }

    private StudentProfile requireStudent(long studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student account not found"));
    }

    private StudentAccountView view(StudentProfile student) {
        return new StudentAccountView(student.getId(), student.getUser().getId(),
                student.getUser().getUsername(), student.getUser().getDisplayName(),
                student.getStudentNumber(), student.getProgram(), student.getSemester(),
                student.getUser().isEnabled(), connectionRepository.countByStudentIdAndStatus(
                        student.getId(), DeviceConnectionStatus.APPROVED));
    }
}
