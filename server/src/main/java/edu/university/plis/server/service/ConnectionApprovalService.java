package edu.university.plis.server.service;

import edu.university.plis.server.domain.AppUser;
import edu.university.plis.server.domain.StudentConnection;
import edu.university.plis.server.domain.StudentProfile;
import edu.university.plis.server.repository.StudentConnectionRepository;
import edu.university.plis.server.repository.StudentProfileRepository;
import edu.university.plis.shared.dto.ConnectionRequestView;
import edu.university.plis.shared.model.DeviceConnectionStatus;
import edu.university.plis.shared.model.RoleName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ConnectionApprovalService {
    private final StudentConnectionRepository connectionRepository;
    private final StudentProfileRepository studentRepository;
    private final AuditService auditService;

    public ConnectionApprovalService(
            StudentConnectionRepository connectionRepository,
            StudentProfileRepository studentRepository,
            AuditService auditService) {
        this.connectionRepository = connectionRepository;
        this.studentRepository = studentRepository;
        this.auditService = auditService;
    }

    @Transactional(noRollbackFor = AccessPendingException.class)
    public String requireApprovedOrRequest(AppUser user, String deviceId, String deviceName) {
        if (user.getRole() != RoleName.ROLE_STUDENT) {
            return null;
        }
        String cleanDeviceId = SetupService.required(deviceId, "Device identity", 128);
        String cleanDeviceName = deviceName == null || deviceName.isBlank()
                ? "Student computer" : SetupService.required(deviceName, "Device name", 160);
        StudentProfile student = studentRepository.findByUserUsernameIgnoreCase(user.getUsername())
                .orElseThrow(() -> new InvalidRequestException("Student profile is missing"));
        Instant now = Instant.now();
        StudentConnection connection = connectionRepository.findByStudentIdAndDeviceId(
                        student.getId(), cleanDeviceId)
                .orElseGet(() -> {
                    StudentConnection created = connectionRepository.save(
                            new StudentConnection(student, cleanDeviceId, cleanDeviceName, now));
                    auditService.record(user.getUsername(), user.getRole(), "CONNECTION_REQUESTED",
                            cleanDeviceName, cleanDeviceId);
                    return created;
                });
        if (connection.getStatus() == DeviceConnectionStatus.TERMINATED) {
            connection.requestAgain(cleanDeviceName, now);
            auditService.record(user.getUsername(), user.getRole(), "CONNECTION_REQUESTED_AGAIN",
                    cleanDeviceName, cleanDeviceId);
        }
        if (connection.getStatus() != DeviceConnectionStatus.APPROVED) {
            throw new AccessPendingException("Access request sent. Waiting for teacher approval");
        }
        connection.seen(now);
        return cleanDeviceId;
    }

    @Transactional(readOnly = true)
    public boolean isApproved(String username, String deviceId) {
        return deviceId != null && connectionRepository
                .existsByStudentUserUsernameIgnoreCaseAndDeviceIdAndStatus(
                        username, deviceId, DeviceConnectionStatus.APPROVED);
    }

    @Transactional
    public void heartbeat(String username, String deviceId) {
        StudentProfile student = studentRepository.findByUserUsernameIgnoreCase(username)
                .orElseThrow(() -> new InvalidRequestException("Student profile is missing"));
        StudentConnection connection = connectionRepository.findByStudentIdAndDeviceId(student.getId(), deviceId)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException(
                        "This device is not approved"));
        if (connection.getStatus() != DeviceConnectionStatus.APPROVED) {
            throw new org.springframework.security.access.AccessDeniedException("Teacher terminated this connection");
        }
        connection.seen(Instant.now());
    }

    @Transactional(readOnly = true)
    public List<ConnectionRequestView> list() {
        return connectionRepository.findAllByOrderByRequestedAtDesc().stream().map(this::view).toList();
    }

    @Transactional
    public ConnectionRequestView decide(long connectionId, boolean approved, String teacherUsername) {
        StudentConnection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Connection request not found"));
        if (approved) {
            connection.approve(Instant.now());
        } else {
            connection.terminate(Instant.now());
        }
        auditService.record(teacherUsername, RoleName.ROLE_TEACHER,
                approved ? "CONNECTION_APPROVED" : "CONNECTION_TERMINATED",
                connection.getStudent().getUser().getUsername() + " / " + connection.getDeviceName(),
                connection.getDeviceId());
        return view(connection);
    }

    @Transactional
    public void terminateStudent(long studentId, String teacherUsername) {
        List<StudentConnection> connections = connectionRepository.findByStudentId(studentId);
        connections.forEach(connection -> connection.terminate(Instant.now()));
        auditService.record(teacherUsername, RoleName.ROLE_TEACHER, "STUDENT_CONNECTIONS_TERMINATED",
                "Student profile " + studentId + ", devices: " + connections.size(), null);
    }

    private ConnectionRequestView view(StudentConnection connection) {
        return new ConnectionRequestView(connection.getId(), connection.getStudent().getId(),
                connection.getStudent().getUser().getUsername(), connection.getStudent().getUser().getDisplayName(),
                connection.getDeviceId(), connection.getDeviceName(), connection.getStatus(),
                connection.getRequestedAt(), connection.getApprovedAt(), connection.getLastSeenAt(),
                connection.getTerminatedAt());
    }
}
