#!/usr/bin/env bash
set -euo pipefail
env_file="${1:-/etc/argus/application-prod.env}"
jar_file="${2:-target/Argus-backend-0.0.1-SNAPSHOT.jar}"
if [[ ! -f "$env_file" || ! -f "$jar_file" ]]; then
  echo "Usage: start-backend.sh <trusted-env-file> <jar-file>" >&2
  exit 1
fi
# Environment file is executable shell syntax and must be controlled by the operator.
set -a
source "$env_file"
set +a
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:--Xms192m -Xmx384m -XX:MaxMetaspaceSize=160m -XX:+UseSerialGC}"
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}"
exec java -jar "$jar_file"
