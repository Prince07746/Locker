package com.guardian.app.event;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "category_events")
public class CategoryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(nullable = false)
    private String category;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    protected CategoryEvent() {}

    public CategoryEvent(UUID deviceId, String category, Instant occurredAt) {
        this.deviceId = deviceId;
        this.category = category;
        this.occurredAt = occurredAt;
        this.receivedAt = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getDeviceId() { return deviceId; }
    public String getCategory() { return category; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getReceivedAt() { return receivedAt; }
}
