#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/lib/android-env.sh"

ensure_android_java_home

cd "$ROOT_DIR/android-app"
gradle_args=(
  --no-daemon
  app:assembleRelease
)

if [[ -n "${TRAINING_API_BASE_URL:-}" ]]; then
  gradle_args+=(
    -PrunningAppTrainingApiBaseUrl="$TRAINING_API_BASE_URL"
  )
fi

./gradlew "${gradle_args[@]}"
