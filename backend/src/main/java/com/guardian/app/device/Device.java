package com.guardian.app.device;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "devices")
public class Device {

    public enum Status { PENDING, ONLINE, OFFLINE }

    @Id
    private UUID id;

    @Column(name = "guardian_id", nullable = false)
    private UUID guardianId;

    private String label;
    private String platform;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "last_status_change", nullable = false)
    private Instant lastStatusChange = Instant.now();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Device() {}

    public Device(UUID id, UUID guardianId, String label, String platform, String tokenHash) {
        this.id = id;
        this.guardianId = guardianId;
        this.label = label;
        this.platform = platform;
        this.tokenHash = tokenHash;
    }

    public void changeStatus(Status newStatus) {
        if (this.status != newStatus) {
            this.status = newStatus;
            this.lastStatusChange = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getGuardianId() { return guardianId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getPlatform() { return platform; }
    public String getTokenHash() { return tokenHash; }
    public Status getStatus() { return status; }
    public Instant getLastStatusChange() { return lastStatusChange; }
    public Instant getCreatedAt() { return createdAt; }
}
