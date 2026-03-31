# VPS Deployment Guide

This is the end-to-end deployment guide for exposing the backend on a Debian or Ubuntu VPS through `systemd` and `nginx`.

The commands are written so they can be executed directly by an operator or an automation agent such as OpenClaw on the target server.

## Target Topology

- backend runs as a `systemd` service
- backend listens only on loopback: `127.0.0.1:8080`
- `nginx` terminates public HTTP/HTTPS
- public traffic goes to `https://api.example.com`

## Prerequisites

- Debian or Ubuntu VPS with `systemd`
- a DNS record pointing your domain to the VPS
- the repository checked out on the VPS
- Go installed on the VPS
- Codex CLI installed on the VPS if you use `RUNNING_APP_PROVIDER=codex`

## 1. Point DNS To The VPS

Create an `A` record:

- `api.example.com -> <VPS_PUBLIC_IP>`

Wait until DNS resolves correctly:

```bash
dig +short api.example.com
```

## 2. Install The Backend Service

From the repo root on the VPS:

```bash
make backend-build
sudo ./backend/deploy/systemd/install-debian.sh
```

If root cannot see your Go installation and you want the installer fallback build path:

```bash
sudo RUNNING_APP_GO_BINARY="$(command -v go)" ./backend/deploy/systemd/install-debian.sh
```

The installer prefers the prebuilt binary from:

```bash
backend/bin/running-app-backend
```

## 3. Configure The Backend Service

Edit:

```bash
sudo editor /etc/running-app/backend.env
```

Recommended baseline for a public deployment behind Nginx:

```env
RUNNING_APP_HTTP_ADDR=127.0.0.1:8080
RUNNING_APP_PROVIDER=codex
RUNNING_APP_REQUEST_TIMEOUT=10m
RUNNING_APP_WRITE_TIMEOUT=11m
RUNNING_APP_CODEX_BINARY=/usr/local/bin/codex
RUNNING_APP_CODEX_WORKDIR=/opt/running-app/backend
RUNNING_APP_CODEX_SANDBOX=read-only
```

Apply the config:

```bash
runningapp restart
runningapp status
```

## 4. Install Nginx Reverse Proxy

From the same repo root:

```bash
sudo RUNNING_APP_SERVER_NAME=api.example.com ./backend/deploy/nginx/install-debian.sh
```

This config proxies public traffic to:

```bash
127.0.0.1:8080
```

## 5. Enable HTTPS

Install Certbot:

```bash
sudo apt-get update
sudo apt-get install -y certbot python3-certbot-nginx
```

Issue and attach the certificate:

```bash
sudo certbot --nginx -d api.example.com
```

## 6. Open Firewall Ports

If you use `ufw`:

```bash
sudo ufw allow OpenSSH
sudo ufw allow 'Nginx Full'
sudo ufw enable
sudo ufw status
```

If your VPS provider has a separate firewall panel, make sure these ports are open:

- `80/tcp`
- `443/tcp`

## 7. Verify The Public Endpoint

Health check:

```bash
curl -i https://api.example.com/healthz
```

Expected result:

- HTTP `200 OK`
- JSON body with `{"status":"ok"}`

Check service logs:

```bash
runningapp logs
```

Check Nginx logs:

```bash
sudo journalctl -u nginx -f
```

## 8. Use The Public Endpoint From Android

Build the app with the public backend URL:

```bash
make android-build TRAINING_API_BASE_URL=https://api.example.com/
```

If you install directly to an emulator or device:

```bash
make android-install TRAINING_API_BASE_URL=https://api.example.com/
```

## Update Flow

On the VPS, update the backend like this:

```bash
cd /path/to/running-app
git pull --ff-only
make backend-build
sudo ./backend/deploy/systemd/install-debian.sh
runningapp status
```

If you changed only `/etc/running-app/backend.env`, a restart is enough:

```bash
runningapp restart
```

If you changed only the Nginx config:

```bash
sudo nginx -t
sudo systemctl reload nginx
```

## Related Files

- `backend/deploy/systemd/README.md`
- `backend/deploy/nginx/README.md`
- `backend/deploy/systemd/install-debian.sh`
- `backend/deploy/nginx/install-debian.sh`
