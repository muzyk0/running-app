#!/usr/bin/env bash
set -euo pipefail

android_java_major_version() {
  local java_home="$1"
  [[ -x "$java_home/bin/java" ]] || return 1

  "$java_home/bin/java" -version 2>&1 |
    awk -F '"' '/version/ {
      split($2, parts, ".")
      if (parts[1] == "1") {
        print parts[2]
      } else {
        print parts[1]
      }
      exit
    }'
}

android_java_home_is_supported() {
  local java_home="${1:-}"
  [[ -n "$java_home" ]] || return 1

  local java_major_version=""
  java_major_version="$(android_java_major_version "$java_home" 2>/dev/null || true)"
  [[ "$java_major_version" == "21" || "$java_major_version" == "17" ]]
}

preferred_android_java_home() {
  [[ -x /usr/libexec/java_home ]] || return 0
  /usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null || true
}

ensure_android_java_home() {
  if android_java_home_is_supported "${JAVA_HOME:-}"; then
    export PATH="$JAVA_HOME/bin:$PATH"
    return
  fi

  local detected_java_home=""
  detected_java_home="$(preferred_android_java_home)"
  if [[ -n "$detected_java_home" ]]; then
    export JAVA_HOME="$detected_java_home"
    export PATH="$JAVA_HOME/bin:$PATH"
    return
  fi

  if [[ -n "${JAVA_HOME:-}" ]]; then
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
}
