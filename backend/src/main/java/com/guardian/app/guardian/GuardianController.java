package com.guardian.app.guardian;

import com.guardian.app.common.ApiExceptions;
import com.guardian.app.device.Device;
import com.guardian.app.device.DeviceDtos.DeviceView;
import com.guardian.app.device.DeviceDtos.RegisterDeviceRequest;
import com.guardian.app.device.DeviceDtos.RegisterDeviceResponse;
import com.guardian.app.device.DeviceService;
import com.guardian.app.event.CategoryEvent;
import com.guardian.app.event.CategoryEventRepository;
import com.guardian.app.security.CurrentGuardian;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/guardian")
public class GuardianController {

    private final DeviceService devices;
    private final CategoryEventRepository events;

    public GuardianController(DeviceService devices, CategoryEventRepository events) {
        this.devices = devices;
        this.events = events;
    }

    public record EventView(String category, Instant occurredAt) {}

    @GetMapping("/devices")
    public List<DeviceView> listDevices() {
        return devices.listForGuardian(CurrentGuardian.id()).stream().map(DeviceView::of).toList();
    }

    @PostMapping("/devices")
    public RegisterDeviceResponse addDevice(@RequestBody RegisterDeviceRequest req) {
        var reg = devices.register(CurrentGuardian.id(), req.label(), req.platform());
        return new RegisterDeviceResponse(reg.device().getId(), reg.rawToken());
    }

    @GetMapping("/devices/{deviceId}/events")
    public List<EventView> deviceEvents(@PathVariable UUID deviceId,
                                        @RequestParam(defaultValue = "50") int limit) {
        Device d = devices.require(deviceId);
        if (!d.getGuardianId().equals(CurrentGuardian.id())) {
            throw ApiExceptions.notFound("Device not found");
        }
        int capped = Math.min(Math.max(limit, 1), 200);
        return events.findByDeviceIdOrderByOccurredAtDesc(deviceId, PageRequest.of(0, capped))
                .stream().map(e -> new EventView(e.getCategory(), e.getOccurredAt())).toList();
    }
}
