#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "run this installer as root: sudo ./backend/deploy/systemd/install-debian.sh" >&2
  exit 1
fi

if [[ ! -f /etc/debian_version ]]; then
  echo "this installer supports Debian/Ubuntu-style systems only" >&2
  exit 1
fi

if ! command -v systemctl >/dev/null 2>&1; then
  echo "systemctl is required" >&2
  exit 1
fi

find_go_bin() {
  if [[ -n "${RUNNING_APP_GO_BINARY:-}" ]]; then
    if [[ -x "${RUNNING_APP_GO_BINARY}" ]]; then
      printf '%s\n' "${RUNNING_APP_GO_BINARY}"
      return 0
    fi

    echo "RUNNING_APP_GO_BINARY is set but not executable: ${RUNNING_APP_GO_BINARY}" >&2
    exit 1
  fi

  if command -v go >/dev/null 2>&1; then
    command -v go
    return 0
  fi

  local candidate=""
  for candidate in /usr/local/go/bin/go /usr/local/bin/go /usr/bin/go; do
    if [[ -x "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done

  if [[ -n "${SUDO_USER:-}" ]] && command -v su >/dev/null 2>&1; then
    local user_go=""
    user_go="$(su - "$SUDO_USER" -c 'command -v go' 2>/dev/null || true)"
    if [[ -n "$user_go" && -x "$user_go" ]]; then
      printf '%s\n' "$user_go"
      return 0
    fi
  fi

  return 1
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
BACKEND_DIR="$REPO_ROOT/backend"
PREBUILT_BINARY_DEFAULT="$BACKEND_DIR/bin/running-app-backend"
SERVICE_NAME="running-app-backend"
SERVICE_USER="running-app"
SERVICE_GROUP="running-app"
APP_ROOT="/opt/running-app"
APP_WORKDIR="$APP_ROOT/backend"
LOG_DIR="/var/log/running-app"
ENV_DIR="/etc/running-app"
ENV_FILE="$ENV_DIR/backend.env"
UNIT_SOURCE="$SCRIPT_DIR/running-app-backend.service"
UNIT_TARGET="/etc/systemd/system/$SERVICE_NAME.service"
ENV_TEMPLATE="$SCRIPT_DIR/backend.env.example"
CTL_SOURCE="$SCRIPT_DIR/runningappctl.sh"
TMP_BINARY="$(mktemp)"
PREBUILT_BINARY="${RUNNING_APP_BINARY:-$PREBUILT_BINARY_DEFAULT}"
GO_BIN=""

cleanup() {
  rm -f "$TMP_BINARY"
}
trap cleanup EXIT

if [[ ! -d "$BACKEND_DIR" ]]; then
  echo "backend source directory not found: $BACKEND_DIR" >&2
  exit 1
fi

if [[ ! -f "$UNIT_SOURCE" ]]; then
  echo "systemd unit file not found: $UNIT_SOURCE" >&2
  exit 1
fi

if [[ ! -f "$ENV_TEMPLATE" ]]; then
  echo "environment template not found: $ENV_TEMPLATE" >&2
  exit 1
fi

if [[ ! -f "$CTL_SOURCE" ]]; then
  echo "service control script not found: $CTL_SOURCE" >&2
  exit 1
fi

if ! getent group "$SERVICE_GROUP" >/dev/null; then
  groupadd --system "$SERVICE_GROUP"
fi

if ! id -u "$SERVICE_USER" >/dev/null 2>&1; then
  useradd \
    --system \
    --gid "$SERVICE_GROUP" \
    --home-dir "$APP_ROOT" \
    --create-home \
    --shell /usr/sbin/nologin \
    "$SERVICE_USER"
fi

install -d -o "$SERVICE_USER" -g "$SERVICE_GROUP" "$APP_ROOT" "$APP_WORKDIR" "$ENV_DIR" "$LOG_DIR"

if [[ -x "$PREBUILT_BINARY" ]]; then
  cp "$PREBUILT_BINARY" "$TMP_BINARY"
else
  GO_BIN="$(find_go_bin || true)"
  if [[ -z "$GO_BIN" ]]; then
    echo "go is required to build the backend binary on the server" >&2
    echo "tip: run 'make backend-build' first or rerun with RUNNING_APP_GO_BINARY=/absolute/path/to/go" >&2
    exit 1
  fi

  cd "$BACKEND_DIR"
  "$GO_BIN" build -trimpath -ldflags="-s -w" -o "$TMP_BINARY" ./cmd/server
fi

install -m 0755 "$TMP_BINARY" /usr/local/bin/running-app-backend
install -m 0755 "$CTL_SOURCE" /usr/local/bin/runningapp

install -m 0644 "$UNIT_SOURCE" "$UNIT_TARGET"

if [[ ! -f "$ENV_FILE" ]]; then
  install -m 0640 -o "$SERVICE_USER" -g "$SERVICE_GROUP" "$ENV_TEMPLATE" "$ENV_FILE"
else
  chown "$SERVICE_USER:$SERVICE_GROUP" "$ENV_FILE"
  chmod 0640 "$ENV_FILE"
fi

systemctl daemon-reload
systemctl enable "$SERVICE_NAME"
systemctl restart "$SERVICE_NAME"

cat <<EOF
Installed $SERVICE_NAME.

Service status:
  runningapp status

Live logs:
  runningapp logs

Environment file:
  $ENV_FILE

If you update the environment file later, restart the service:
  runningapp restart
EOF
