#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
THRESHOLD="${BACKEND_COVERAGE_THRESHOLD:-80}"
CRITICAL_PACKAGES=(
  ./internal/api
  ./internal/config
  ./internal/prompt
  ./internal/provider/codexcli
  ./internal/schema
  ./internal/service
)

cd "$ROOT_DIR/backend"

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

go test ./... -coverprofile=coverage.out
go tool cover -func=coverage.out | tail -n 1

for pkg in "${CRITICAL_PACKAGES[@]}"; do
  output_file="$tmp_dir/$(printf '%s' "$pkg" | tr '/.' '__').log"
  go test -cover "$pkg" 2>&1 | tee "$output_file"

  coverage_percent="$(
    grep -Eo 'coverage: [0-9.]+%' "$output_file" | tail -n 1 | awk '{print $2}' | tr -d '%'
  )"
  if [[ -z "$coverage_percent" ]]; then
    echo "Unable to determine coverage for $pkg" >&2
    exit 1
  fi

  if ! awk -v coverage="$coverage_percent" -v threshold="$THRESHOLD" 'BEGIN { exit ((coverage + 0) >= (threshold + 0)) ? 0 : 1 }'; then
    echo "Coverage for $pkg is ${coverage_percent}% which is below ${THRESHOLD}%." >&2
    exit 1
  fi
done
