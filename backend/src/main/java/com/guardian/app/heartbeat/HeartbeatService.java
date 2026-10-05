package com.guardian.app.heartbeat;

import com.guardian.app.config.AppProperties;
import com.guardian.app.device.Device;
import com.guardian.app.device.DeviceRepository;
import com.guardian.app.event.CategoryEventRepository;
import com.guardian.app.guardian.Guardian;
import com.guardian.app.guardian.GuardianRepository;
import com.guardian.app.notify.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Heartbeats are high-frequency and low-value individually, so they are NOT
 * written to Postgres one row at a time. Each beat just refreshes a Redis key.
 * A periodic sweep turns the *absence* of recent beats into a single durable
 * "device offline" transition — which is the real safety signal, because a
 * forced uninstall we cannot prevent still makes the device go silent.
 */
@Service
public class HeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatService.class);
    private static final String HB_PREFIX = "hb:";

    private final StringRedisTemplate redis;
    private final DeviceRepository devices;
    private final GuardianRepository guardians;
    private final CategoryEventRepository events;
    private final NotificationService notifier;
    private final AppProperties props;

    public HeartbeatService(StringRedisTemplate redis, DeviceRepository devices,
                            GuardianRepository guardians, CategoryEventRepository events,
                            NotificationService notifier, AppProperties props) {
        this.redis = redis;
        this.devices = devices;
        this.guardians = guardians;
        this.events = events;
        this.notifier = notifier;
        this.props = props;
    }

    public void touch(UUID deviceId) {
        // Keep the key around a little longer than the timeout so the sweep,
        // not key expiry, owns the transition decision.
        Duration ttl = Duration.ofSeconds(props.heartbeatTimeoutSeconds() * 3);
        redis.opsForValue().set(HB_PREFIX + deviceId, Long.toString(Instant.now().toEpochMilli()), ttl);
    }

    private boolean isAlive(UUID deviceId) {
        String v = redis.opsForValue().get(HB_PREFIX + deviceId);
        if (v == null) return false;
        long last = Long.parseLong(v);
        return (Instant.now().toEpochMilli() - last) <= props.heartbeatTimeoutSeconds() * 1000L;
    }

    /** Detects online/offline transitions once per minute and alerts the guardian on "gone dark". */
    @Scheduled(fixedDelayString = "${app.sweep-interval-ms:60000}")
    public void sweep() {
        List<Device> tracked = devices.findByStatusIn(List.of(Device.Status.ONLINE, Device.Status.OFFLINE));
        for (Device d : tracked) {
            boolean alive = isAlive(d.getId());
            if (alive && d.getStatus() != Device.Status.ONLINE) {
                d.changeStatus(Device.Status.ONLINE);
                devices.save(d);
                log.info("Device {} recovered", d.getId());
            } else if (!alive && d.getStatus() == Device.Status.ONLINE) {
                d.changeStatus(Device.Status.OFFLINE);
                devices.save(d);
                alertGuardian(d, "Protection went offline",
                        "The protected software on \"" + safeLabel(d) + "\" has stopped reporting. "
                        + "This can mean the device is off, or that protection was removed or disabled. "
                        + "Please check the device.");
            }
        }
    }

    /** Daily retention purge of category events. */
    @Scheduled(cron = "${app.purge-cron:0 30 3 * * *}")
    public void purgeOldEvents() {
        Instant cutoff = Instant.now().minus(props.eventRetentionDays(), ChronoUnit.DAYS);
        int removed = events.deleteOlderThan(cutoff);
        if (removed > 0) log.info("Purged {} expired category events", removed);
    }

    private void alertGuardian(Device d, String subject, String body) {
        Optional<Guardian> g = guardians.findById(d.getGuardianId());
        g.ifPresent(guardian -> notifier.send(guardian.getEmail(), subject, body));
    }

    private String safeLabel(Device d) {
        return d.getLabel() != null ? d.getLabel() : "your device";
    }
}
