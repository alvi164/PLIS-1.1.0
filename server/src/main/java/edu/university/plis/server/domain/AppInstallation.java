package edu.university.plis.server.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "app_installation")
public class AppInstallation {
    @Id
    private Long id = 1L;

    @Column(name = "installation_id", nullable = false, unique = true, length = 64)
    private String installationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AppInstallation() {
    }

    public AppInstallation(String installationId, Instant createdAt) {
        this.installationId = installationId;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getInstallationId() { return installationId; }
    public Instant getCreatedAt() { return createdAt; }
}
