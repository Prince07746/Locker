package com.guardian.app.uninstall;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "uninstall_attempts")
public class UninstallAttempt {

    public enum Status { PENDING, APPROVED, FAILED, EXPIRED }

    @Id
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected UninstallAttempt() {}

    public UninstallAttempt(UUID id, UUID deviceId) {
        this.id = id;
        this.deviceId = deviceId;
    }

    public void resolve(Status status) {
        this.status = status;
        this.resolvedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getDeviceId() { return deviceId; }
    public Status getStatus() { return status; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}
