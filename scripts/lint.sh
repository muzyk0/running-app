#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

shell_files=()
while IFS= read -r file; do
  shell_files+=("$file")
done < <(find "$ROOT_DIR/scripts" -type f -name '*.sh' | sort)
if ((${#shell_files[@]} > 0)); then
  bash -n "${shell_files[@]}"
fi

go_files=()
while IFS= read -r file; do
  go_files+=("$file")
done < <(find "$ROOT_DIR/backend" -type f -name '*.go' | sort)
if ((${#go_files[@]} > 0)); then
  unformatted="$(gofmt -l "${go_files[@]}")"
  if [[ -n "$unformatted" ]]; then
    printf 'Unformatted Go files:\n%s\n' "$unformatted" >&2
    exit 1
  fi
fi

(cd "$ROOT_DIR/backend" && go vet ./...)
"$ROOT_DIR/scripts/android-gradle-smoke.sh"
