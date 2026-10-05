# Locker agent (Windows, v1)

The device-side program. v1 enforces with a **local DNS filter**: it runs a
resolver on `127.0.0.1`, points the system at it, blocks listed domains
(answering `0.0.0.0`), reports the category to your server, and forwards
everything else upstream. It also heartbeats and gates its own removal through
the guardian-authorized flow.

## What's here

```
cmd/locker-agent      entry point + subcommands
internal/config       reads %ProgramData%\Locker\config.json
internal/backend      server client (heartbeat, events, uninstall)
internal/filter       blocklist + DNS proxy (the actual blocking)
internal/sysdns       points Windows DNS at the local resolver, restores it
internal/service      Windows service harness + install/remove + auto-restart
internal/uninstall    guardian-authorized removal
```

## Build (on Windows, with Go 1.22+)

```powershell
cd agent
go mod tidy                      # fetches miekg/dns and golang.org/x/sys
go build -o locker-agent.exe ./cmd/locker-agent
```

## Test without installing a service

Create `%ProgramData%\Locker\config.json` and `blocklist.txt` (copy the sample
from `installer/`), then from an **elevated** prompt:

```powershell
.\locker-agent.exe console
```

Binding `127.0.0.1:53` and changing system DNS both require admin rights.

## Package the installer

1. Build and **code-sign** `locker-agent.exe` (EV certificate).
2. Install Inno Setup 6+, then `iscc installer\locker.iss`.
3. Sign the resulting `LockerSetup.exe` too.

The installer collects the server URL / device ID / token, writes the config,
disables browser DoH via policy, registers the auto-restarting service, and
wires Add/Remove Programs through the guardian-authorized uninstall.

## Honest limitations (v1)

- **DNS granularity only.** Blocks whole domains, not in-page keywords. Deeper
  inspection needs the native filtering engine (a later workstream).
- **DoH / VPN can tunnel past DNS.** The installer disables browser DoH and the
  blocklist blocks common DoH resolvers, but a VPN or a manually configured
  encrypted resolver can still bypass it. Detecting those is future work.
- **An administrator can still force removal** (safe mode, stopping the service
  from recovery media). This is unpreventable on a general-purpose OS; the
  heartbeat turns it into a "device went dark" alert, which is the real
  guarantee.
- **Not signed here.** Unsigned, SmartScreen and antivirus will block it. An EV
  certificate and AV whitelisting are required before distribution.
- **Single internal watchdog.** The service auto-restarts via SCM recovery; a
  separate paired watchdog process is a planned hardening step.
- **Untested in this form.** Build and test on real Windows before any use.
```
