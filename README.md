# Content Guardian — backend + agent client

A privacy-first accountability backend for a cross-platform content blocker,
plus the cross-platform (Go) portion of the device agent.

## What this is — and what it honestly is not

This repository is the part of the system that **reduces cleanly to code**: the
backend and the agent's client logic. Running `docker compose up` gives you a
working, TLS-terminated API with database migrations, encryption at rest, the
server-authorized uninstall flow, heartbeat-based offline detection, and
minimal (content-free) event reporting.

It is deliberately **not** a finished shippable product, because three parts of
the full system are separate platform workstreams that cannot be a code drop:

1. **Filter enforcement** — Windows WFP callout / local DNS proxy, and macOS
   `NEFilterDataProvider` / `NEDNSProxyProvider`. These are native modules
   (C/C++ and Swift) that call this agent client to report verdicts.
2. **On-device ML classifier** — the model that produces category verdicts
   locally so that no URL or page content ever leaves the machine.
3. **Code signing / notarization / AV whitelisting** — an EV cert (Windows),
   Apple Developer ID + notarization (macOS), and AV-vendor whitelist
   submissions. Start these early; issuance has real lead time.

Treat this as a production-shaped foundation to build those onto, and run your
own security review before go-live.

## Privacy and data protection

The system is built around data minimization. It stores **no browsing content**
— no URLs, page text, keystrokes, or screenshots. What little personal data
exists is protected in depth:

- Guardian email **encrypted at rest** with AES-256-GCM; the key lives in the
  environment, never in the database, so a DB dump alone is useless.
- A deterministic **HMAC lookup hash** lets us find a guardian by email without
  storing or decrypting plaintext.
- Guardian passwords hashed with **bcrypt**.
- **Opaque** device IDs and per-device tokens; only the SHA-256 of a token is
  stored, compared in constant time. No hardware identifiers.
- Category events carry a **label only** and are auto-purged after a
  configurable retention window.
- **TLS everywhere** via Caddy's automatic HTTPS; DB and Redis are not exposed
  to the host network.
- Heartbeat and one-time codes live in Redis with short TTLs — ephemeral by
  design.

If you later choose URL-level detail for stronger accountability, encrypt that
payload end-to-end to the guardian's key so the server stays zero-knowledge.

## Run it locally

```bash
cd deploy
cp .env.example .env            # then fill in secrets
# generate the three keys:
#   openssl rand -base64 32     # once each for CRYPTO, HMAC, JWT
docker compose up --build
```

Point your domain at the host and set it in `Caddyfile` for real TLS. For local
testing you can reach the app through Caddy or expose port 8080 on `app`.

## API surface

Guardian (JWT via `Authorization: Bearer`):
- `POST /api/v1/auth/register` `{ email, password }` → `{ guardianId, token }`
- `POST /api/v1/auth/login` `{ email, password }` → `{ token }`
- `GET  /api/v1/guardian/devices`
- `POST /api/v1/guardian/devices` `{ label, platform }` → `{ deviceId, deviceToken }` (token shown once)
- `GET  /api/v1/guardian/devices/{id}/events?limit=50`

Agent (per-device via `X-Device-Token`):
- `POST /api/v1/devices/{id}/heartbeat`
- `POST /api/v1/devices/{id}/events` `{ category, occurredAt? }`
- `POST /api/v1/devices/{id}/uninstall/request` → `{ attemptId }` (alerts guardian + mails code)
- `POST /api/v1/devices/{id}/uninstall/verify` `{ attemptId, code }` → `{ uninstallToken }`

## Agent client

```bash
cd agent
GUARDIAN_BASE_URL=https://api.yourdomain.com \
GUARDIAN_DEVICE_ID=... \
GUARDIAN_DEVICE_TOKEN=... \
go run agent.go
```

## Layout

```
backend/   Spring Boot API (Java 21): auth, devices, events, uninstall, heartbeat
agent/     Go agent client: heartbeat, buffered reporter, uninstall flow
deploy/    docker-compose + Caddy + env template
```
