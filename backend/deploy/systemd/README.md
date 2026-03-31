# Debian Systemd Install

This backend can be installed as a `systemd` daemon on Debian or Ubuntu with the repo-owned installer:

```bash
sudo ./backend/deploy/systemd/install-debian.sh
```

## What the installer does

- creates the `running-app` system user and group
- creates runtime directories under `/opt/running-app`, `/etc/running-app`, and `/var/log/running-app`
- builds the backend binary from the checked-out repo
- installs the binary as `/usr/local/bin/running-app-backend`
- installs the helper CLI as `/usr/local/bin/runningapp`
- installs the `systemd` unit from `backend/deploy/systemd/running-app-backend.service`
- creates `/etc/running-app/backend.env` from `backend/deploy/systemd/backend.env.example` if it does not already exist
- enables and restarts the `running-app-backend` service

## Prerequisites

- Debian or Ubuntu with `systemd`
- the repository checked out on the server
- Go installed on the server and available in `PATH`
- the Codex CLI installed if you use `RUNNING_APP_PROVIDER=codex`

## Install

From the repo root:

```bash
sudo ./backend/deploy/systemd/install-debian.sh
```

## Post-install checks

```bash
runningapp status
runningapp logs
```

## Service control shortcuts

After installation, use the helper CLI:

```bash
runningapp start
runningapp stop
runningapp restart
runningapp status
runningapp logs
```

## Configuration

The `systemd` service reads its configuration from:

```bash
/etc/running-app/backend.env
```

That file is separate from the local development `.env` support used when you run the backend directly from the repository.

After changing `/etc/running-app/backend.env`, restart the service:

```bash
runningapp restart
```

## Notes

- The default listener is `127.0.0.1:8080`, which is the right choice if you put Nginx or Caddy in front of the service.
- If you want to expose the backend directly, change `RUNNING_APP_HTTP_ADDR` in `/etc/running-app/backend.env`.
- The installer does not install the Codex CLI for you. If `RUNNING_APP_PROVIDER=codex`, make sure `RUNNING_APP_CODEX_BINARY` points to a working CLI binary on the server.
