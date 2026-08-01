package edu.university.plis.server.service;

import edu.university.plis.server.domain.AppUser;
import edu.university.plis.server.repository.AppUserRepository;
import edu.university.plis.server.repository.StudentProfileRepository;
import edu.university.plis.server.repository.TeacherProfileRepository;
import edu.university.plis.server.security.AuthTokenService;
import edu.university.plis.shared.dto.LoginResponse;
import edu.university.plis.shared.dto.LoginRequest;
import edu.university.plis.shared.model.RoleName;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {
    private final AuthenticationManager authenticationManager;
    private final AppUserRepository userRepository;
    private final TeacherProfileRepository teacherRepository;
    private final StudentProfileRepository studentRepository;
    private final AuthTokenService tokenService;
    private final ConnectionApprovalService connectionApprovalService;
    private final AuditService auditService;

    public AuthenticationService(
            AuthenticationManager authenticationManager,
            AppUserRepository userRepository,
            TeacherProfileRepository teacherRepository,
            StudentProfileRepository studentRepository,
            AuthTokenService tokenService,
            ConnectionApprovalService connectionApprovalService,
            AuditService auditService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.teacherRepository = teacherRepository;
        this.studentRepository = studentRepository;
        this.tokenService = tokenService;
        this.connectionApprovalService = connectionApprovalService;
        this.auditService = auditService;
    }

    public LoginResponse login(LoginRequest request) {
        String username = request == null ? null : request.username();
        String password = request == null ? null : request.password();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new InvalidRequestException("Username and password are required");
        }
        authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username.trim(), password));
        AppUser user = userRepository.findByUsernameIgnoreCase(username.trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String deviceId = connectionApprovalService.requireApprovedOrRequest(
                user, request.deviceId(), request.deviceName());
        AuthTokenService.IssuedToken issuedToken = tokenService.issueToken(user, deviceId);
        auditService.record(user.getUsername(), user.getRole(), "LOGIN_SUCCESS",
                request.deviceName(), deviceId);
        return new LoginResponse(issuedToken.value(), "Bearer", issuedToken.expiresAt(), user.getId(),
                resolveProfileId(user), user.getUsername(), user.getDisplayName(), user.getRole());
    }

    private Long resolveProfileId(AppUser user) {
        if (user.getRole() == RoleName.ROLE_STUDENT) {
            return studentRepository.findByUserUsernameIgnoreCase(user.getUsername())
                    .map(student -> student.getId()).orElse(null);
        }
        return teacherRepository.findByUserUsernameIgnoreCase(user.getUsername())
                .map(teacher -> teacher.getId()).orElse(null);
    }
}
