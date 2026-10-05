package com.guardian.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All security-sensitive values come from the environment, never hard-coded.
 *
 * @param cryptoKey     base64-encoded 32-byte key for AES-256-GCM (email at rest)
 * @param emailHmacKey  base64-encoded key for the deterministic email lookup hash
 * @param jwtSigningKey base64-encoded key (>=32 bytes) for signing guardian + uninstall tokens
 * @param fromAddress   verified "from" address used with the transactional email relay
 * @param heartbeatTimeoutSeconds  grace period before a silent device is marked OFFLINE
 * @param eventRetentionDays       category events older than this are purged
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String cryptoKey,
        String emailHmacKey,
        String jwtSigningKey,
        String fromAddress,
        long heartbeatTimeoutSeconds,
        int eventRetentionDays
) {}
