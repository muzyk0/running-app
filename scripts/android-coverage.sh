#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/lib/android-env.sh"

ensure_android_java_home

cd "$ROOT_DIR/android-app"
./gradlew --no-daemon \
  app:jacocoDebugUnitTestReport \
  app:jacocoDebugUnitTestCoverageVerification
