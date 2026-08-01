package edu.university.plis.server.domain;

import edu.university.plis.shared.model.DeviceConnectionStatus;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "student_connections", uniqueConstraints = @UniqueConstraint(
        name = "uk_student_device", columnNames = {"student_id", "device_id"}), indexes = {
        @Index(name = "idx_connection_status", columnList = "connection_status,requested_at"),
        @Index(name = "idx_connection_device", columnList = "device_id")
})
public class StudentConnection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @Column(name = "device_id", nullable = false, length = 128)
    private String deviceId;

    @Column(name = "device_name", nullable = false, length = 160)
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false, length = 16)
    private DeviceConnectionStatus status = DeviceConnectionStatus.PENDING;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "terminated_at")
    private Instant terminatedAt;

    protected StudentConnection() {
    }

    public StudentConnection(StudentProfile student, String deviceId, String deviceName, Instant now) {
        this.student = student;
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.requestedAt = now;
        this.lastSeenAt = now;
    }

    public void requestAgain(String newDeviceName, Instant now) {
        deviceName = newDeviceName;
        status = DeviceConnectionStatus.PENDING;
        requestedAt = now;
        approvedAt = null;
        terminatedAt = null;
        lastSeenAt = now;
    }

    public void approve(Instant now) {
        status = DeviceConnectionStatus.APPROVED;
        approvedAt = now;
        terminatedAt = null;
        lastSeenAt = now;
    }

    public void terminate(Instant now) {
        status = DeviceConnectionStatus.TERMINATED;
        terminatedAt = now;
    }

    public void seen(Instant now) { lastSeenAt = now; }

    public Long getId() { return id; }
    public StudentProfile getStudent() { return student; }
    public String getDeviceId() { return deviceId; }
    public String getDeviceName() { return deviceName; }
    public DeviceConnectionStatus getStatus() { return status; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getTerminatedAt() { return terminatedAt; }
}
