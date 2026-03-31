#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

go_files=()
while IFS= read -r file; do
  go_files+=("$file")
done < <(find "$ROOT_DIR/backend" -type f -name '*.go' | sort)

if ((${#go_files[@]} > 0)); then
  gofmt -w "${go_files[@]}"
fi
