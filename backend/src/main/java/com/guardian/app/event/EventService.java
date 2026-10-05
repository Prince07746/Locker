package com.guardian.app.event;

import com.guardian.app.device.Device;
import com.guardian.app.guardian.Guardian;
import com.guardian.app.guardian.GuardianRepository;
import com.guardian.app.notify.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class EventService {

    private final CategoryEventRepository events;
    private final GuardianRepository guardians;
    private final NotificationService notifier;

    public EventService(CategoryEventRepository events, GuardianRepository guardians,
                        NotificationService notifier) {
        this.events = events;
        this.guardians = guardians;
        this.notifier = notifier;
    }

    /**
     * Records a category verdict produced on-device. The payload contains a
     * category label only -- never a URL or any page content.
     */
    @Transactional
    public void record(Device device, String category, Instant occurredAt) {
        events.save(new CategoryEvent(device.getId(), category, occurredAt));
        Guardian g = guardians.findById(device.getGuardianId()).orElse(null);
        if (g == null) return;
        String label = device.getLabel() != null ? device.getLabel() : "a protected device";
        notifier.send(g.getEmail(),
                "Blocked content accessed (" + category + ")",
                "On \"" + label + "\", an attempt to access " + category
                        + " content was detected and blocked at " + occurredAt + ".");
    }
}
