-- Privacy-first schema. We deliberately store NO browsing content:
-- no URLs, no page text, no keystrokes, no screenshots. Only coarse
-- category verdicts produced on-device, plus the minimum needed to
-- authenticate devices and notify guardians.

CREATE TABLE guardians (
    id               UUID PRIMARY KEY,
    -- Email encrypted at rest (AES-256-GCM). The key lives in the app
    -- environment, never in this database, so a DB dump alone is useless.
    email_enc        TEXT        NOT NULL,
    -- Deterministic HMAC-SHA-256 of the normalized email. Lets us look a
    -- guardian up by email without decrypting or storing plaintext.
    email_lookup     CHAR(64)    NOT NULL UNIQUE,
    password_hash    TEXT        NOT NULL,            -- bcrypt
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE devices (
    id                   UUID PRIMARY KEY,            -- opaque; no hardware id
    guardian_id          UUID        NOT NULL REFERENCES guardians(id) ON DELETE CASCADE,
    label                TEXT,                         -- e.g. "Son's laptop" (set by guardian)
    platform             TEXT,                         -- windows | macos
    -- SHA-256 of the per-device bearer token. The raw token is shown to the
    -- installer exactly once and never stored.
    token_hash           CHAR(64)    NOT NULL,
    status               TEXT        NOT NULL DEFAULT 'PENDING', -- PENDING|ONLINE|OFFLINE
    last_status_change   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_devices_guardian ON devices(guardian_id);

-- Coarse category verdicts. No content, ever.
CREATE TABLE category_events (
    id           BIGSERIAL PRIMARY KEY,
    device_id    UUID        NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    category     TEXT        NOT NULL,                 -- e.g. "adult", "gambling"
    occurred_at  TIMESTAMPTZ NOT NULL,                 -- when the device observed it
    received_at  TIMESTAMPTZ NOT NULL DEFAULT now()    -- used for retention purge
);
CREATE INDEX idx_events_device_time ON category_events(device_id, occurred_at DESC);
CREATE INDEX idx_events_received ON category_events(received_at);

-- Every uninstall attempt is recorded and the guardian is notified at
-- request time, regardless of whether it ultimately succeeds.
CREATE TABLE uninstall_attempts (
    id           UUID PRIMARY KEY,
    device_id    UUID        NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    status       TEXT        NOT NULL DEFAULT 'PENDING', -- PENDING|APPROVED|FAILED|EXPIRED
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at  TIMESTAMPTZ
);
CREATE INDEX idx_uninstall_device ON uninstall_attempts(device_id, requested_at DESC);
