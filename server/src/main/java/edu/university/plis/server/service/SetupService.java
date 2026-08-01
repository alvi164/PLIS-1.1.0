package edu.university.plis.server.service;

import edu.university.plis.server.domain.AppInstallation;
import edu.university.plis.server.domain.AppUser;
import edu.university.plis.server.domain.TeacherProfile;
import edu.university.plis.server.repository.AppInstallationRepository;
import edu.university.plis.server.repository.AppUserRepository;
import edu.university.plis.server.repository.TeacherProfileRepository;
import edu.university.plis.shared.dto.InitialTeacherSetupRequest;
import edu.university.plis.shared.dto.SetupStatus;
import edu.university.plis.shared.model.RoleName;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class SetupService {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,40}");

    private final AppInstallationRepository installationRepository;
    private final AppUserRepository userRepository;
    private final TeacherProfileRepository teacherRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public SetupService(
            AppInstallationRepository installationRepository,
            AppUserRepository userRepository,
            TeacherProfileRepository teacherRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService) {
        this.installationRepository = installationRepository;
        this.userRepository = userRepository;
        this.teacherRepository = teacherRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public SetupStatus status() {
        AppInstallation installation = installation();
        String teacherName = teacherRepository.findFirstByOrderByIdAsc()
                .map(profile -> profile.getUser().getDisplayName()).orElse(null);
        return new SetupStatus(userRepository.countByRole(RoleName.ROLE_TEACHER) == 0,
                installation.getInstallationId(), teacherName, "1.1.0");
    }

    @Transactional
    public synchronized SetupStatus initializeTeacher(InitialTeacherSetupRequest request) {
        if (userRepository.countByRole(RoleName.ROLE_TEACHER) > 0) {
            throw new ConflictException("Initial teacher setup has already been completed");
        }
        String username = username(request == null ? null : request.username());
        String displayName = required(request.displayName(), "Teacher name", 120);
        String password = password(request.password());
        AppUser user = userRepository.save(new AppUser(username, passwordEncoder.encode(password),
                RoleName.ROLE_TEACHER, displayName));
        teacherRepository.save(new TeacherProfile(user, null));
        auditService.record(username, RoleName.ROLE_TEACHER, "INITIAL_TEACHER_CREATED",
                "First-run owner account created", null);
        return status();
    }

    private AppInstallation installation() {
        return installationRepository.findById(1L)
                .orElseGet(() -> installationRepository.save(
                        new AppInstallation(UUID.randomUUID().toString(), Instant.now())));
    }

    static String username(String value) {
        String username = required(value, "Username", 40);
        if (!USERNAME.matcher(username).matches()) {
            throw new InvalidRequestException(
                    "Username must be 3-40 letters, numbers, dots, underscores, or hyphens");
        }
        return username;
    }

    static String password(String value) {
        if (value == null || value.length() < 5 || value.length() > 128) {
            throw new InvalidRequestException("Password must contain 5-128 characters");
        }
        return value;
    }

    static String required(String value, String label, int max) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(label + " is required");
        }
        String clean = value.strip();
        if (clean.length() > max) {
            throw new InvalidRequestException(label + " must be at most " + max + " characters");
        }
        return clean;
    }
}
