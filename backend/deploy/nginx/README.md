# Debian Nginx Reverse Proxy

This directory contains a repo-owned Nginx reverse proxy setup for the backend service.

Use it together with the `systemd` backend service and keep the backend bound to loopback:

```env
RUNNING_APP_HTTP_ADDR=127.0.0.1:8080
```

## Files

- `running-app-backend.conf`: Nginx server block template
- `install-debian.sh`: Debian/Ubuntu installer for Nginx and the site config

## One-click install

From the repo root:

```bash
sudo RUNNING_APP_SERVER_NAME=api.example.com ./backend/deploy/nginx/install-debian.sh
```

Optional override for a custom backend upstream:

```bash
sudo RUNNING_APP_SERVER_NAME=api.example.com \
  RUNNING_APP_UPSTREAM_ADDR=127.0.0.1:8080 \
  ./backend/deploy/nginx/install-debian.sh
```

## What the installer does

- installs `nginx`
- renders `running-app-backend.conf` with your `server_name` and upstream address
- installs the site config into `/etc/nginx/sites-available/running-app-backend.conf`
- enables the site through `/etc/nginx/sites-enabled/`
- disables the default Nginx site if it is still enabled
- runs `nginx -t`
- enables and reloads the `nginx` service

## TLS

The installer configures plain HTTP only. For production, add HTTPS with Certbot:

```bash
sudo apt-get install -y certbot python3-certbot-nginx
sudo certbot --nginx -d api.example.com
```

## Why Nginx is useful here

- terminates TLS
- serves the app through a stable domain
- keeps the Go service private on `127.0.0.1:8080`
- raises proxy timeouts so long training generation requests are not cut off after the Nginx defaults

The provided config sets proxy timeouts to 11 minutes, which matches the current backend request budget.
