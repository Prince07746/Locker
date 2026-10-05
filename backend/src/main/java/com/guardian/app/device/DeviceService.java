package com.guardian.app.device;

import com.guardian.app.common.ApiExceptions;
import com.guardian.app.common.RandomTokens;
import com.guardian.app.crypto.HashUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DeviceService {

    private final DeviceRepository repo;

    public DeviceService(DeviceRepository repo) {
        this.repo = repo;
    }

    public record Registration(Device device, String rawToken) {}

    /** Guardian registers a new device; the raw token is returned exactly once. */
    @Transactional
    public Registration register(UUID guardianId, String label, String platform) {
        String rawToken = RandomTokens.opaqueToken();
        Device d = new Device(UUID.randomUUID(), guardianId, label, platform, HashUtil.sha256Hex(rawToken));
        repo.save(d);
        return new Registration(d, rawToken);
    }

    /** Authenticates an agent request. Constant-time comparison over the token hash. */
    public Device authenticate(UUID deviceId, String rawToken) {
        Device d = repo.findById(deviceId)
                .orElseThrow(() -> ApiExceptions.unauthorized("Unknown device"));
        if (!HashUtil.constantTimeEquals(d.getTokenHash(), HashUtil.sha256Hex(rawToken))) {
            throw ApiExceptions.unauthorized("Bad device token");
        }
        return d;
    }

    public Device require(UUID deviceId) {
        return repo.findById(deviceId).orElseThrow(() -> ApiExceptions.notFound("Device not found"));
    }

    public List<Device> listForGuardian(UUID guardianId) {
        return repo.findByGuardianId(guardianId);
    }
}
