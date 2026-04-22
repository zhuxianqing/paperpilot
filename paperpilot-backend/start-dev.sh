#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
APP_CONFIG="$SCRIPT_DIR/src/main/resources/application-dev.yml"
BACKEND_URL="${APP_URL:-http://localhost:8080}"
JAVA17_HOME="/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home"

print_usage() {
  cat <<'EOF'
Usage: ./start-dev.sh

Starts the PaperPilot backend locally with Java 17.
This script does not start Docker services.
It assumes MySQL and Redis are already running locally.

Optional environment overrides:
  JAVA17_HOME       Java 17 home directory
  DB_USERNAME       Database username override for application-dev.yml
  DB_PASSWORD       Database password override for application-dev.yml
  JWT_SECRET        JWT secret for local development
  ENCRYPTION_KEY    32-byte AES key for local development
  APP_URL           Backend base URL (default: http://localhost:8080)
EOF
}

require_command() {
  local command_name="$1"

  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Missing required command: $command_name" >&2
    exit 1
  fi
}

java_major_version() {
  local java_bin="$1"

  "$java_bin" -version 2>&1 | awk -F '"' '/version/ {print $2; exit}' | cut -d. -f1
}

javac_major_version() {
  local javac_bin="$1"

  "$javac_bin" -version 2>&1 | awk '{print $2}' | cut -d. -f1
}

resolve_java17_home() {
  if [[ -n "${JAVA17_HOME:-}" ]]; then
    echo "$JAVA17_HOME"
    return
  fi

  if [[ -n "${JAVA_HOME:-}" ]] && [[ -x "$JAVA_HOME/bin/java" ]] && [[ -x "$JAVA_HOME/bin/javac" ]]; then
    if [[ "$(java_major_version "$JAVA_HOME/bin/java")" == "17" ]] && [[ "$(javac_major_version "$JAVA_HOME/bin/javac")" == "17" ]]; then
      echo "$JAVA_HOME"
      return
    fi
  fi

  if [[ "$(uname -s)" == "Darwin" ]] && [[ -x "/usr/libexec/java_home" ]]; then
    local detected_java_home
    detected_java_home="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
    if [[ -n "$detected_java_home" ]]; then
      echo "$detected_java_home"
      return
    fi
  fi

  echo ""
}

verify_java17() {
  local java_home="$1"
  local java_bin="$java_home/bin/java"
  local javac_bin="$java_home/bin/javac"

  if [[ ! -x "$java_bin" || ! -x "$javac_bin" ]]; then
    echo "Invalid Java 17 home: $java_home" >&2
    exit 1
  fi

  if [[ "$(java_major_version "$java_bin")" != "17" || "$(javac_major_version "$javac_bin")" != "17" ]]; then
    echo "Java 17 is required. Current JAVA_HOME: $java_home" >&2
    echo "Set JAVA17_HOME to your JDK 17 path and try again." >&2
    exit 1
  fi
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  print_usage
  exit 0
fi

if [[ ! -f "$APP_CONFIG" ]]; then
  echo "Missing config file: $APP_CONFIG" >&2
  exit 1
fi

require_command mvn

JAVA_HOME="$(resolve_java17_home)"
if [[ -z "$JAVA_HOME" ]]; then
  echo "Unable to locate Java 17." >&2
  echo "Install JDK 17 or set JAVA17_HOME to your JDK 17 path." >&2
  exit 1
fi

verify_java17 "$JAVA_HOME"

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"
export JWT_SECRET="${JWT_SECRET:-paperpilot-dev-jwt-secret-key-2026!!}"
export ENCRYPTION_KEY="${ENCRYPTION_KEY:-paperpilot-dev-encryption-key-32}"
export APP_URL="$BACKEND_URL"

echo "Using JAVA_HOME: $JAVA_HOME"
"$JAVA_HOME/bin/java" -version
echo "Starting PaperPilot backend at $BACKEND_URL"
echo "This script does not start MySQL or Redis."
echo "Make sure local services match $APP_CONFIG"

exec mvn -f "$SCRIPT_DIR/pom.xml" -s /Users/zhuxianqing/maven/setting.xml -Dmaven.compiler.executable="$JAVA_HOME/bin/javac" spring-boot:run
