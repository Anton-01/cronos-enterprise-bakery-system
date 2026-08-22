#!/usr/bin/env bash
# Starts the qa stack (docker-compose.qa.yml): full packaged image, SPRING_PROFILES_ACTIVE=qa,
# secrets from an external keys.properties (KeysPropertiesEnvironmentPostProcessor — fail-fast if
# missing/malformed). See docs/RUNBOOK.md for the full walkthrough.
#
# Usage: ./scripts/start-qa.sh /path/to/qa/keys.properties
#    or: CRONOS_KEYS_FILE_HOST=/path/to/qa/keys.properties ./scripts/start-qa.sh
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

if ! command -v docker >/dev/null 2>&1; then
    echo "docker is not installed or not on PATH. Install Docker and retry." >&2
    exit 1
fi
if ! docker compose version >/dev/null 2>&1; then
    echo "docker compose (v2, the 'docker compose' subcommand) is required." >&2
    exit 1
fi

CRONOS_KEYS_FILE_HOST="${1:-${CRONOS_KEYS_FILE_HOST:-}}"
if [ -z "$CRONOS_KEYS_FILE_HOST" ]; then
    echo "Usage: $0 /path/to/qa/keys.properties" >&2
    echo "(or set CRONOS_KEYS_FILE_HOST). See keys.properties.example for the expected format." >&2
    exit 1
fi
if [ ! -f "$CRONOS_KEYS_FILE_HOST" ]; then
    echo "No file found at: $CRONOS_KEYS_FILE_HOST" >&2
    echo "Create it from keys.properties.example first." >&2
    exit 1
fi
export CRONOS_KEYS_FILE_HOST

echo "Building and starting the qa stack (detached) — SPRING_PROFILES_ACTIVE=qa."
echo "Secrets: $CRONOS_KEYS_FILE_HOST"
echo
docker compose -f docker-compose.qa.yml up -d --build

echo
echo "API:        http://localhost:9191/api/v1"
echo "Grafana:    http://localhost:3000"
echo "Prometheus: http://localhost:9090"
echo
echo "Logs:   docker compose -f docker-compose.qa.yml logs -f app"
echo "Stop:   docker compose -f docker-compose.qa.yml down"
