package com.guardian.app.device;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

public final class DeviceDtos {

    public record RegisterDeviceRequest(String label, String platform) {}

    /** rawToken is shown exactly once; the agent must persist it securely. */
    public record RegisterDeviceResponse(UUID deviceId, String deviceToken) {}

    public record EventReportRequest(@NotBlank String category, Instant occurredAt) {}

    public record DeviceView(UUID id, String label, String platform, String status, Instant lastStatusChange) {
        public static DeviceView of(Device d) {
            return new DeviceView(d.getId(), d.getLabel(), d.getPlatform(),
                    d.getStatus().name(), d.getLastStatusChange());
        }
    }
}
