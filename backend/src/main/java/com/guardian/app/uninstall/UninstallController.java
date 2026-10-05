package com.guardian.app.uninstall;

import com.guardian.app.device.Device;
import com.guardian.app.device.DeviceService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/devices/{deviceId}/uninstall")
public class UninstallController {

    private final DeviceService devices;
    private final UninstallService uninstall;

    public UninstallController(DeviceService devices, UninstallService uninstall) {
        this.devices = devices;
        this.uninstall = uninstall;
    }

    public record VerifyRequest(@NotNull UUID attemptId, @NotBlank String code) {}

    /** Step 1 — begins an attempt, alerts the guardian, mails a one-time code. */
    @PostMapping("/request")
    public Map<String, Object> request(@PathVariable UUID deviceId,
                                       @RequestHeader("X-Device-Token") String token) {
        Device device = devices.authenticate(deviceId, token);
        UUID attemptId = uninstall.requestUninstall(device);
        return Map.of("attemptId", attemptId);
    }

    /** Step 2 — verifies the code; on success returns a signed, short-lived uninstall token. */
    @PostMapping("/verify")
    public Map<String, Object> verify(@PathVariable UUID deviceId,
                                      @RequestHeader("X-Device-Token") String token,
                                      @RequestBody VerifyRequest req) {
        Device device = devices.authenticate(deviceId, token);
        String uninstallToken = uninstall.verify(device, req.attemptId(), req.code());
        return Map.of("uninstallToken", uninstallToken);
    }
}
