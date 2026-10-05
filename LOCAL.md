# Run the whole stack locally (server + desktop agent)

## Quick start — one command

From the **repo root** (the folder that contains `run-local.sh`, `backend/`,
`agent/`, `deploy/`):

```bash
./run-local.sh
```

That script (works with Rancher Desktop — it detects `docker compose` or
`nerdctl compose`): generates local secrets into `deploy/.env`, builds and
starts the stack, waits until the API is healthy, auto-creates a guardian and a
device, and writes a ready-to-use agent config to `agent-config.local.json`. It
then prints the exact Windows steps for the desktop agent.

- Fake email inbox (watch alerts here): `http://localhost:8025`
- API: `http://localhost:8080`
- Stop: `./run-local.sh down`  ·  Wipe data: `./run-local.sh reset`  ·  Logs: `./run-local.sh logs`

> On Windows, run it from **Git Bash** (which ships with `bash`, `curl`, and
> `/dev/urandom`). The desktop agent still builds and runs only on Windows.

The rest of this document is the manual, step-by-step version of what the script
does, if you want to run each piece yourself.

---

This runs everything on your own machine with no VPS, no domain, and no real
emails sent — alerts are captured in a local web inbox. The server runs
anywhere Docker runs; the **agent only builds/runs on Windows**, so for the
desktop half you need a Windows machine (or VM) with Go installed.

---

## Part 1 — Server locally

### 1. Prerequisites
- Docker Desktop.

### 2. Configure `.env`
```bash
cd deploy
cp .env.example .env
```
Edit `.env`. Generate the three keys with `openssl rand -base64 32` (run three
times) and set local mail values:
```
DB_PASSWORD=localdev
APP_CRYPTO_KEY=<generated>
APP_EMAIL_HMAC_KEY=<generated>
APP_JWT_SIGNING_KEY=<generated>
SMTP_HOST=mailpit
SMTP_PORT=1025
SMTP_USER=
SMTP_PASSWORD=
SMTP_AUTH=false
SMTP_STARTTLS=false
APP_FROM_ADDRESS=alerts@local.test
```

### 3. Start it
```bash
docker compose -f docker-compose.local.yml up --build
```
- API: `http://localhost:8080`
- Fake inbox (watch every alert here): `http://localhost:8025`

### 4. Verify
```bash
curl http://localhost:8080/actuator/health        # {"status":"UP"}
```

---

## Part 2 — Create a guardian and a device

Register a guardian (returns a JWT):
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"parent@local.test","password":"a-strong-passphrase"}'
```
Copy the `token` from the response, then add a device:
```bash
TOKEN=paste-the-jwt-here
curl -s -X POST http://localhost:8080/api/v1/guardian/devices \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"label":"Test PC","platform":"windows"}'
```
The response has `deviceId` and `deviceToken` — **save both** (the token is
shown only once).

---

## Part 3 — Desktop agent locally (Windows)

### 1. Build
```powershell
cd agent
go mod tidy
go build -o locker-agent.exe ./cmd/locker-agent
```

### 2. Configure
Create `C:\ProgramData\Locker\config.json` (use a high port for safe testing so
you don't collide with the Windows DNS service on 53):
```json
{
  "serverBaseUrl": "http://localhost:8080",
  "deviceId": "<deviceId from Part 2>",
  "deviceToken": "<deviceToken from Part 2>",
  "upstreamDns": "9.9.9.9:53",
  "listenAddr": "127.0.0.1:5353",
  "heartbeatSeconds": 60
}
```
Copy the sample blocklist to `C:\ProgramData\Locker\blocklist.txt` (from
`installer/blocklist.txt`). Add a domain you can actually test, e.g.:
```
adult example.com
```

### 3. Run it (safe mode — does NOT touch your real DNS)
```powershell
.\locker-agent.exe console --no-redirect
```
Leave it running. In another terminal:
```powershell
# Blocked domain -> resolves to 0.0.0.0
nslookup -port=5353 example.com 127.0.0.1
# Allowed domain -> real answer, forwarded upstream
nslookup -port=5353 wikipedia.org 127.0.0.1
```
Now open `http://localhost:8025` — you should see a **"Blocked content
accessed"** email for the category you hit. The server also marks the device
ONLINE from the heartbeats.

### 4. Full enforcement test (optional, changes your DNS)
Set `"listenAddr": "127.0.0.1:53"`, run an **elevated** PowerShell, and:
```powershell
.\locker-agent.exe console
```
This repoints your machine's DNS at the agent, so a blocked domain fails to
load in the browser directly. Press Ctrl-C to stop and automatically restore
DNS. (If it crashes without restoring, run `.\locker-agent.exe` with the
redirect off and reset DNS to DHCP in Network settings.)

---

## Part 4 — Test the guardian-authorized uninstall

With `config.json` in place:
```powershell
.\locker-agent.exe uninstall
```
It calls the server, which emails the guardian two messages — check
`http://localhost:8025` for the **"Uninstall attempt detected"** alert and the
**"Your uninstall authorization code"** email. Enter that 6-digit code at the
prompt. A correct code authorizes removal; a wrong/absent one is refused. This
exercises the exact flow the installer uses for Add/Remove Programs.

---

## Part 5 — Device-offline alert

Stop the agent (close the console). Within a few minutes the server's sweep
notices the missing heartbeats and emails a **"Protection went offline"** alert
to `http://localhost:8025`. That is the signal that covers a forced removal you
can't prevent.

---

## Teardown
```bash
cd deploy
docker compose -f docker-compose.local.yml down          # keep data
docker compose -f docker-compose.local.yml down -v        # wipe data
```

---

## Notes
- The agent build only works on Windows; the server runs on any OS.
- `console --no-redirect` + a high `listenAddr` port is the safe way to iterate
  without disturbing your machine. Use port 53 + elevation only for the full
  browser-level test.
- Everything here is HTTP on localhost. Real deployments use HTTPS — see
  `DEPLOY.md`.
