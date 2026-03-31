#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

"$ROOT_DIR/scripts/backend-coverage.sh"

cd "$ROOT_DIR/backend"
go vet ./...

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required for backend-ci" >&2
  exit 1
fi

docker build -t running-app-backend .
