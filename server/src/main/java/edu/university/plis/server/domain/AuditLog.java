package edu.university.plis.server.domain;

import edu.university.plis.shared.model.RoleName;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_actor_time", columnList = "actor_username,event_timestamp"),
        @Index(name = "idx_audit_action_time", columnList = "action,event_timestamp")
})
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_username", nullable = false, length = 80)
    private String actorUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", nullable = false, length = 24)
    private RoleName actorRole;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(length = 2000)
    private String details;

    @Column(name = "device_id", length = 128)
    private String deviceId;

    @Column(name = "event_timestamp", nullable = false)
    private Instant timestamp;

    protected AuditLog() {
    }

    public AuditLog(String actorUsername, RoleName actorRole, String action,
                    String details, String deviceId, Instant timestamp) {
        this.actorUsername = actorUsername;
        this.actorRole = actorRole;
        this.action = action;
        this.details = details;
        this.deviceId = deviceId;
        this.timestamp = timestamp;
    }

    public Long getId() { return id; }
    public String getActorUsername() { return actorUsername; }
    public RoleName getActorRole() { return actorRole; }
    public String getAction() { return action; }
    public String getDetails() { return details; }
    public String getDeviceId() { return deviceId; }
    public Instant getTimestamp() { return timestamp; }
}
