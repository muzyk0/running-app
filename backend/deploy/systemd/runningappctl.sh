#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="${RUNNING_APP_SERVICE_NAME:-running-app-backend}"

usage() {
  cat <<EOF
Usage: runningapp <command>

Commands:
  start      Start the backend service
  stop       Stop the backend service
  restart    Restart the backend service
  status     Show service status
  logs       Tail service logs
  enable     Enable service autostart
  disable    Disable service autostart
EOF
}

run_privileged() {
  if [[ "${EUID}" -eq 0 ]]; then
    "$@"
    return
  fi

  if command -v sudo >/dev/null 2>&1; then
    sudo "$@"
    return
  fi

  echo "this command requires root or sudo" >&2
  exit 1
}

if [[ $# -lt 1 ]]; then
  usage
  exit 1
fi

case "$1" in
  start)
    run_privileged systemctl start "$SERVICE_NAME"
    ;;
  stop)
    run_privileged systemctl stop "$SERVICE_NAME"
    ;;
  restart)
    run_privileged systemctl restart "$SERVICE_NAME"
    ;;
  status)
    run_privileged systemctl status "$SERVICE_NAME"
    ;;
  logs)
    run_privileged journalctl -u "$SERVICE_NAME" -f
    ;;
  enable)
    run_privileged systemctl enable "$SERVICE_NAME"
    ;;
  disable)
    run_privileged systemctl disable "$SERVICE_NAME"
    ;;
  *)
    usage
    exit 1
    ;;
esac
