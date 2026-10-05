package com.guardian.app.uninstall;

import com.guardian.app.common.ApiExceptions;
import com.guardian.app.common.RandomTokens;
import com.guardian.app.crypto.HashUtil;
import com.guardian.app.device.Device;
import com.guardian.app.guardian.Guardian;
import com.guardian.app.guardian.GuardianRepository;
import com.guardian.app.notify.NotificationService;
import com.guardian.app.security.TokenService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

/**
 * The "password to uninstall" is really a one-time code delivered only to the
 * guardian's inbox. Crucially, the guardian is alerted the moment an attempt
 * starts -- before it can possibly succeed -- so every attempt is visible
 * whether or not it is ever approved. No secret capable of authorizing an
 * uninstall is ever stored on the device.
 */
@Service
public class UninstallService {

    private static final String CODE_PREFIX = "uninstall:code:";
    private static final Duration CODE_TTL = Duration.ofMinutes(15);
    private static final long TOKEN_TTL_SECONDS = 300; // 5 minutes

    private final UninstallAttemptRepository attempts;
    private final GuardianRepository guardians;
    private final NotificationService notifier;
    private final TokenService tokens;
    private final StringRedisTemplate redis;

    public UninstallService(UninstallAttemptRepository attempts, GuardianRepository guardians,
                            NotificationService notifier, TokenService tokens, StringRedisTemplate redis) {
        this.attempts = attempts;
        this.guardians = guardians;
        this.notifier = notifier;
        this.tokens = tokens;
        this.redis = redis;
    }

    /** Step 1: record the attempt, alert the guardian, and mail them a one-time code. */
    @Transactional
    public UUID requestUninstall(Device device) {
        UninstallAttempt attempt = attempts.save(new UninstallAttempt(UUID.randomUUID(), device.getId()));

        String code = RandomTokens.numericCode(6);
        redis.opsForValue().set(CODE_PREFIX + attempt.getId(), HashUtil.sha256Hex(code), CODE_TTL);

        Guardian g = guardians.findById(device.getGuardianId()).orElse(null);
        if (g != null) {
            String label = device.getLabel() != null ? device.getLabel() : "a protected device";
            notifier.send(g.getEmail(), "Uninstall attempt detected",
                    "Someone is trying to remove the protection on \"" + label + "\". "
                            + "If this was not you, do not share any code. "
                            + "If you authorize it, the one-time code is in a separate email.");
            notifier.send(g.getEmail(), "Your uninstall authorization code",
                    "Authorization code for \"" + label + "\": " + code
                            + "\nThis code expires in 15 minutes. Only enter it on the device if you intend "
                            + "to allow protection to be removed.");
        }
        return attempt.getId();
    }

    /** Step 2: verify the code and, if valid, issue a short-lived signed uninstall token. */
    @Transactional
    public String verify(Device device, UUID attemptId, String code) {
        UninstallAttempt attempt = attempts.findById(attemptId)
                .orElseThrow(() -> ApiExceptions.notFound("No such uninstall attempt"));
        if (!attempt.getDeviceId().equals(device.getId())) {
            throw ApiExceptions.unauthorized("Attempt does not belong to this device");
        }

        String key = CODE_PREFIX + attemptId;
        String expected = redis.opsForValue().get(key);
        if (expected == null) {
            attempt.resolve(UninstallAttempt.Status.EXPIRED);
            attempts.save(attempt);
            throw ApiExceptions.badRequest("Code expired; request a new one");
        }
        if (!HashUtil.constantTimeEquals(expected, HashUtil.sha256Hex(code))) {
            attempt.resolve(UninstallAttempt.Status.FAILED);
            attempts.save(attempt);
            throw ApiExceptions.unauthorized("Incorrect code");
        }

        redis.delete(key);
        attempt.resolve(UninstallAttempt.Status.APPROVED);
        attempts.save(attempt);
        return tokens.issueUninstallToken(device.getId(), TOKEN_TTL_SECONDS);
    }
}
