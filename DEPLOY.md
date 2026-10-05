# Deploying Content Guardian on a fresh Linux VPS

This walks you from a brand-new Ubuntu server to a running, HTTPS-secured API
with one `docker compose` command. Assumes Ubuntu 22.04/24.04 and that you can
SSH in as `root` or a sudo user. Commands are copy-paste ready.

---

## 0. What you'll end up with

Four containers on a private network: Caddy (HTTPS), the Spring Boot app,
Postgres, and Redis. Only ports 80 and 443 are open to the world; the database
and Redis are never exposed. Caddy fetches and renews a real Let's Encrypt
certificate automatically.

---

## 1. Log in and update the system

```bash
ssh root@YOUR_SERVER_IP
apt update && apt upgrade -y
```

(Optional but recommended) create a non-root user and use it from here on:

```bash
adduser deploy
usermod -aG sudo deploy
# then log out and back in as: ssh deploy@YOUR_SERVER_IP
```

---

## 2. Install Docker + the Compose plugin

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
# log out and back in once so the group change takes effect, then verify:
docker --version
docker compose version
```

---

## 3. Open the firewall

Allow SSH and web traffic only:

```bash
sudo apt install -y ufw
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw --force enable
sudo ufw status
```

Do **not** open 5432 (Postgres) or 6379 (Redis) — they stay internal.

---

## 4. Get the code onto the server

```bash
sudo apt install -y git
git clone https://github.com/Prince07746/Locker.git
cd Locker
```

(Later, to update: `git pull` then rebuild — see section 9.)

---

## 5. Give the server a hostname for HTTPS

You have no domain, so use a free DuckDNS subdomain — it gives you a real
hostname that Let's Encrypt will issue a certificate for.

1. Go to https://www.duckdns.org, sign in, create a subdomain, e.g.
   `myguardian.duckdns.org`.
2. Set its IP to your VPS's public IP (the DuckDNS dashboard has an IP field).
3. Confirm it resolves: `ping myguardian.duckdns.org` should show your VPS IP.

Then edit the Caddyfile to use it:

```bash
nano deploy/Caddyfile
```

Replace the contents with (use YOUR subdomain):

```
myguardian.duckdns.org {
    encode gzip
    reverse_proxy app:8080
}
```

> **Quick test without any hostname (HTTP only, NOT for production):**
> set the Caddyfile to `:80 { reverse_proxy app:8080 }` and reach the API at
> `http://YOUR_SERVER_IP`. Traffic is unencrypted, so only use this to confirm
> the stack boots — never with real guardian accounts.

---

## 6. Configure secrets

```bash
cd deploy
cp .env.example .env
```

Generate the three keys (run three times, paste each into `.env`):

```bash
openssl rand -base64 32
```

Then edit `.env`:

```bash
nano .env
```

Fill in:
- `DB_PASSWORD` — a strong random password.
- `APP_CRYPTO_KEY`, `APP_EMAIL_HMAC_KEY`, `APP_JWT_SIGNING_KEY` — the three
  generated keys.
- `SMTP_HOST / SMTP_PORT / SMTP_USER / SMTP_PASSWORD` — from your transactional
  email provider (Postmark, Amazon SES, or Mailgun). **Do not self-host SMTP** —
  alerts from a fresh VPS IP land in spam, which breaks the core feature.
- `APP_FROM_ADDRESS` — a verified sender address at that provider.

The `.env` file is already git-ignored, so it won't be pushed back to GitHub.

---

## 7. Launch

From the `deploy/` directory:

```bash
docker compose up -d --build
```

`-d` runs it in the background. The first build takes a few minutes (it compiles
the app). Flyway creates the database tables automatically on first start.

---

## 8. Verify it's up

```bash
docker compose ps                 # all services should be "running"/"healthy"
docker compose logs -f app        # watch the app boot; Ctrl-C to stop watching
```

Health check (replace with your hostname):

```bash
curl https://myguardian.duckdns.org/actuator/health
# expect: {"status":"UP"}
```

Create a guardian account to confirm the full path works:

```bash
curl -X POST https://myguardian.duckdns.org/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"a-strong-passphrase"}'
# expect: {"guardianId":"...","token":"..."}
```

---

## 9. Day-to-day operations

**It restarts itself.** Every service uses `restart: unless-stopped`, so it
survives reboots and crashes. Nothing else needed to "keep it running."

**View logs:** `docker compose logs -f app`

**Stop / start:** `docker compose down` / `docker compose up -d`

**Update to a new version:**
```bash
cd ~/Locker
git pull
cd deploy
docker compose up -d --build
```

**Back up the database** (do this — it holds guardian accounts):
```bash
docker compose exec -T postgres pg_dump -U guardian guardian > backup-$(date +%F).sql
```
Automate it with a daily cron job and copy the dump off the server:
```bash
crontab -e
# add:
0 3 * * * cd ~/Locker/deploy && docker compose exec -T postgres pg_dump -U guardian guardian > ~/backups/guardian-$(date +\%F).sql
```

---

## 10. Security checklist before real use

- [ ] Real hostname with working HTTPS (section 5), not HTTP-by-IP.
- [ ] Firewall on; only 22/80/443 open (section 3).
- [ ] SSH hardened — key-only login, root login disabled.
- [ ] The three keys and `DB_PASSWORD` are strong, unique, and **only** in
      `.env` (never committed).
- [ ] Email provider verified and sending (test an alert end to end).
- [ ] Automated, off-server database backups running.
- [ ] A security review of the app before onboarding real users.

---

## Where the agent fits

This VPS runs the **server** only. The desktop agent is installed separately on
each protected Windows/Mac machine and is pointed at your API with:

```
GUARDIAN_BASE_URL=https://myguardian.duckdns.org
GUARDIAN_DEVICE_ID=...        # from POST /api/v1/guardian/devices
GUARDIAN_DEVICE_TOKEN=...     # returned once at device registration
```
