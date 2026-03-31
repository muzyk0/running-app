#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "run this installer as root: sudo ./backend/deploy/nginx/install-debian.sh" >&2
  exit 1
fi

if [[ ! -f /etc/debian_version ]]; then
  echo "this installer supports Debian/Ubuntu-style systems only" >&2
  exit 1
fi

if ! command -v apt-get >/dev/null 2>&1; then
  echo "apt-get is required" >&2
  exit 1
fi

if ! command -v systemctl >/dev/null 2>&1; then
  echo "systemctl is required" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONF_TEMPLATE="$SCRIPT_DIR/running-app-backend.conf"
SITE_NAME="running-app-backend.conf"
SITE_AVAILABLE="/etc/nginx/sites-available/$SITE_NAME"
SITE_ENABLED="/etc/nginx/sites-enabled/$SITE_NAME"
SERVER_NAME="${RUNNING_APP_SERVER_NAME:-_}"
UPSTREAM_ADDR="${RUNNING_APP_UPSTREAM_ADDR:-127.0.0.1:8080}"
TMP_CONF="$(mktemp)"

cleanup() {
  rm -f "$TMP_CONF"
}
trap cleanup EXIT

if [[ ! -f "$CONF_TEMPLATE" ]]; then
  echo "nginx config template not found: $CONF_TEMPLATE" >&2
  exit 1
fi

apt-get update
apt-get install -y nginx

sed \
  -e "s|__SERVER_NAME__|$SERVER_NAME|g" \
  -e "s|__UPSTREAM_ADDR__|$UPSTREAM_ADDR|g" \
  "$CONF_TEMPLATE" > "$TMP_CONF"

install -m 0644 "$TMP_CONF" "$SITE_AVAILABLE"

ln -sfn "$SITE_AVAILABLE" "$SITE_ENABLED"

if [[ -L /etc/nginx/sites-enabled/default ]]; then
  rm -f /etc/nginx/sites-enabled/default
fi

nginx -t
systemctl enable nginx
systemctl reload nginx

cat <<EOF
Installed nginx reverse proxy for Running App backend.

Site config:
  $SITE_AVAILABLE

Current values:
  server_name=$SERVER_NAME
  upstream=$UPSTREAM_ADDR

Validation:
  sudo nginx -t

Logs:
  sudo journalctl -u nginx -f

If you use a real domain, the next step is TLS:
  sudo apt-get install -y certbot python3-certbot-nginx
  sudo certbot --nginx -d $SERVER_NAME
EOF
