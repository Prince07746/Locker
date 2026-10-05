package com.guardian.app.device;

import com.guardian.app.device.DeviceDtos.EventReportRequest;
import com.guardian.app.event.EventService;
import com.guardian.app.heartbeat.HeartbeatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/devices/{deviceId}")
public class AgentController {

    private final DeviceService devices;
    private final HeartbeatService heartbeat;
    private final EventService events;

    public AgentController(DeviceService devices, HeartbeatService heartbeat, EventService events) {
        this.devices = devices;
        this.heartbeat = heartbeat;
        this.events = events;
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(@PathVariable UUID deviceId,
                                          @RequestHeader("X-Device-Token") String token) {
        devices.authenticate(deviceId, token);
        heartbeat.touch(deviceId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/events")
    public ResponseEntity<Void> reportEvent(@PathVariable UUID deviceId,
                                            @RequestHeader("X-Device-Token") String token,
                                            @Valid @RequestBody EventReportRequest req) {
        Device device = devices.authenticate(deviceId, token);
        Instant occurredAt = req.occurredAt() != null ? req.occurredAt() : Instant.now();
        events.record(device, req.category(), occurredAt);
        return ResponseEntity.accepted().build();
    }
}
