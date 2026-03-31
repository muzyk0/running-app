#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/lib/android-env.sh"

ensure_android_java_home

TRAINING_API_BASE_URL="${TRAINING_API_BASE_URL:-http://10.0.2.2:8080/}"

cd "$ROOT_DIR/android-app"
./gradlew --no-daemon \
  app:assembleDebug \
  -PrunningAppTrainingApiBaseUrl="$TRAINING_API_BASE_URL"
