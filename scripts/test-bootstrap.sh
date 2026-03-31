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

grep -q '^toolchain go1.26.1$' "$ROOT_DIR/backend/go.mod"
grep -q '^rootProject.name = "running-app-android"$' "$ROOT_DIR/android-app/settings.gradle.kts"
grep -q '^distributionUrl=.*gradle-9.3.1-bin.zip$' "$ROOT_DIR/android-app/gradle/wrapper/gradle-wrapper.properties"

"$ROOT_DIR/scripts/android-gradle-smoke.sh"
"$ROOT_DIR/scripts/backend-go-smoke.sh"
