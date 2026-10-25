#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

JAR="${SCRIPT_DIR}/fireball-1.0.0.jar"
if [[ ! -f "$JAR" ]]; then
    # Avoid matching fireball-server-*.jar
    JAR="$(ls -1 "${SCRIPT_DIR}"/fireball-[0-9]*.jar 2>/dev/null | head -n1 || true)"
fi

if [[ -z "${JAR}" || ! -f "$JAR" ]]; then
    echo "Error: fireball-*.jar not found next to this script."
    echo "Looked in: $SCRIPT_DIR"
    exit 1
fi

if ! command -v java >/dev/null 2>&1; then
    echo "Error: Java is not installed or not on PATH."
    echo "Install Java 17+ and try again."
    exit 1
fi

exec java -jar "$JAR" "$@"
