#!/usr/bin/env bash
# One command to run the whole Content Guardian server stack locally.
#
#   ./run-local.sh            bring everything up + enroll a test device
#   ./run-local.sh down       stop (keep data)
#   ./run-local.sh reset      stop and WIPE data
#   ./run-local.sh logs       tail the app logs
#   ./run-local.sh enroll     re-run guardian/device enrollment only
#
# Works with Rancher Desktop (dockerd/moby or containerd backend). No manual
# directory juggling: it always operates on its own deploy/ folder.

set -euo pipefail

# --- locate ourselves, regardless of where the script is called from ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$SCRIPT_DIR/deploy"
COMPOSE_FILE="docker-compose.local.yml"
ENV_FILE="$DEPLOY_DIR/.env"
AGENT_CFG="$SCRIPT_DIR/agent-config.local.json"

API="http://localhost:8080"
INBOX="http://localhost:8025"

GUARDIAN_EMAIL="${GUARDIAN_EMAIL:-parent@local.test}"
GUARDIAN_PASSWORD="${GUARDIAN_PASSWORD:-a-strong-local-passphrase}"
DEVICE_LABEL="${DEVICE_LABEL:-Test PC}"

# --- pick a compose command (Rancher Desktop provides one of these) ---
detect_compose() {
  if docker compose version >/dev/null 2>&1; then
    echo "docker compose"
  elif nerdctl compose version >/dev/null 2>&1; then
    echo "nerdctl compose"
  else
    echo ""
  fi
}
COMPOSE="$(detect_compose)"
if [ -z "$COMPOSE" ]; then
  echo "ERROR: no 'docker compose' or 'nerdctl compose' found."
  echo "In Rancher Desktop, make sure the container engine is running."
  exit 1
fi

compose() { ( cd "$DEPLOY_DIR" && $COMPOSE -f "$COMPOSE_FILE" "$@" ); }

# --- base64 32-byte key without requiring openssl (works in git-bash too) ---
genkey() { head -c 32 /dev/urandom | base64 | tr -d '\n'; }

ensure_env() {
  if [ -f "$ENV_FILE" ]; then
    echo "Using existing $ENV_FILE"
    return
  fi
  echo "Generating $ENV_FILE with fresh local secrets..."
  cat > "$ENV_FILE" <<EOF
DB_PASSWORD=localdev
APP_CRYPTO_KEY=$(genkey)
APP_EMAIL_HMAC_KEY=$(genkey)
APP_JWT_SIGNING_KEY=$(genkey)
SMTP_HOST=mailpit
SMTP_PORT=1025
SMTP_USER=
SMTP_PASSWORD=
SMTP_AUTH=false
SMTP_STARTTLS=false
APP_FROM_ADDRESS=alerts@local.test
EOF
}

wait_healthy() {
  echo -n "Waiting for the API to come up"
  for _ in $(seq 1 60); do
    if curl -fs "$API/actuator/health" >/dev/null 2>&1; then
      echo " — up."
      return 0
    fi
    echo -n "."
    sleep 2
  done
  echo
  echo "API did not become healthy in time. Check: ./run-local.sh logs"
  exit 1
}

# pull "key":"value" out of a JSON blob without needing jq
json_get() { echo "$1" | grep -o "\"$2\":\"[^\"]*\"" | head -1 | sed "s/.*:\"\\(.*\\)\"/\\1/"; }

enroll() {
  echo "Enrolling a guardian and device..."
  # register (or log in if the account already exists)
  local reg code body token
  reg="$(curl -s -w $'\n%{http_code}' -X POST "$API/api/v1/auth/register" \
        -H 'Content-Type: application/json' \
        -d "{\"email\":\"$GUARDIAN_EMAIL\",\"password\":\"$GUARDIAN_PASSWORD\"}")"
  code="$(echo "$reg" | tail -1)"
  body="$(echo "$reg" | sed '$d')"
  if [ "$code" = "200" ] || [ "$code" = "201" ]; then
    token="$(json_get "$body" token)"
  else
    # already exists (409) or similar -> log in
    body="$(curl -s -X POST "$API/api/v1/auth/login" \
          -H 'Content-Type: application/json' \
          -d "{\"email\":\"$GUARDIAN_EMAIL\",\"password\":\"$GUARDIAN_PASSWORD\"}")"
    token="$(json_get "$body" token)"
  fi
  if [ -z "$token" ]; then
    echo "Could not obtain a guardian token. Response was:"; echo "$body"; exit 1
  fi

  local dev deviceId deviceToken
  dev="$(curl -s -X POST "$API/api/v1/guardian/devices" \
        -H "Authorization: Bearer $token" \
        -H 'Content-Type: application/json' \
        -d "{\"label\":\"$DEVICE_LABEL\",\"platform\":\"windows\"}")"
  deviceId="$(json_get "$dev" deviceId)"
  deviceToken="$(json_get "$dev" deviceToken)"
  if [ -z "$deviceId" ] || [ -z "$deviceToken" ]; then
    echo "Device enrollment failed. Response was:"; echo "$dev"; exit 1
  fi

  cat > "$AGENT_CFG" <<EOF
{
  "serverBaseUrl": "$API",
  "deviceId": "$deviceId",
  "deviceToken": "$deviceToken",
  "upstreamDns": "9.9.9.9:53",
  "listenAddr": "127.0.0.1:5353",
  "heartbeatSeconds": 60
}
EOF
  echo "Wrote agent config -> $AGENT_CFG"
}

print_next_steps() {
  cat <<EOF

========================================================================
Server is running locally.
  API inbox (fake email): $INBOX
  API base URL:           $API
  Guardian login:         $GUARDIAN_EMAIL / $GUARDIAN_PASSWORD

Agent config was written to:
  $AGENT_CFG

DESKTOP AGENT (Windows only) — next steps on a Windows machine with Go:
  1. Copy this repo's  agent/  folder to the Windows machine (or clone it).
  2. In PowerShell:
         cd agent
         go mod tidy
         go build -o locker-agent.exe .\cmd\locker-agent
  3. Copy the config:
         mkdir C:\ProgramData\Locker 2>$null
         copy $AGENT_CFG C:\ProgramData\Locker\config.json
         copy ..\installer\blocklist.txt C:\ProgramData\Locker\blocklist.txt
     (If the server is on a DIFFERENT machine than the agent, edit
      serverBaseUrl in config.json to that machine's IP, e.g. http://192.168.1.10:8080)
  4. Run the filter safely (does NOT change your DNS, uses port 5353):
         .\locker-agent.exe console --no-redirect
  5. Test it:
         nslookup -port=5353 example.com 127.0.0.1     # blocked -> 0.0.0.0
     Then watch $INBOX for the alert email.

Stop the stack:   ./run-local.sh down
Wipe everything:  ./run-local.sh reset
========================================================================
EOF
}

case "${1:-up}" in
  up)
    ensure_env
    echo "Building and starting containers with: $COMPOSE"
    compose up -d --build
    wait_healthy
    enroll
    print_next_steps
    ;;
  enroll)
    wait_healthy
    enroll
    print_next_steps
    ;;
  down)
    compose down
    ;;
  reset)
    compose down -v
    echo "Stopped and wiped local data."
    ;;
  logs)
    compose logs -f app
    ;;
  *)
    echo "Usage: ./run-local.sh [up|down|reset|logs|enroll]"
    exit 1
    ;;
esac
