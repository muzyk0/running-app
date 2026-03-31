#!/usr/bin/env bash
set -euo pipefail

ensure_android_java_home() {
  if [[ -n "${JAVA_HOME:-}" ]]; then
    export PATH="$JAVA_HOME/bin:$PATH"
    return
  fi

  if [[ -x /usr/libexec/java_home ]]; then
    local detected_java_home=""
    detected_java_home="$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null || true)"
    if [[ -n "$detected_java_home" ]]; then
      export JAVA_HOME="$detected_java_home"
      export PATH="$JAVA_HOME/bin:$PATH"
    fi
  fi
}
