#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required for docker-smoke" >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "docker daemon is not available" >&2
  exit 1
fi

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

cat <<'EOF' > "$tmp_dir/Dockerfile"
FROM golang:1.26.1-alpine
WORKDIR /workspace/backend
COPY go.mod ./
RUN go mod download
EOF

docker build -f "$tmp_dir/Dockerfile" "$ROOT_DIR/backend" >/dev/null
