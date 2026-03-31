#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

mkdir -p "$ROOT_DIR/backend/bin"

cd "$ROOT_DIR/backend"
go build -o bin/running-app-backend ./cmd/server
