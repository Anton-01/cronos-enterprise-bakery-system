#!/usr/bin/env bash
# Starts the production stack (docker-compose.prod.yml): full packaged image,
# SPRING_PROFILES_ACTIVE=prod, secrets from an external keys.properties
# (KeysPropertiesEnvironmentPostProcessor — fail-fast if missing/malformed). Read the SECURITY
# comment at the top of docker-compose.prod.yml before running this on a real host. See
# docs/RUNBOOK.md for the full walkthrough.
#
# Usage: ./scripts/start-prod.sh /etc/cronos/keys.properties
#    or: CRONOS_KEYS_FILE_HOST=/etc/cronos/keys.properties ./scripts/start-prod.sh
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
    echo "Usage: $0 /etc/cronos/keys.properties" >&2
    echo "(or set CRONOS_KEYS_FILE_HOST). See keys.properties.example for the expected format." >&2
    exit 1
fi
if [ ! -f "$CRONOS_KEYS_FILE_HOST" ]; then
    echo "No file found at: $CRONOS_KEYS_FILE_HOST" >&2
    echo "Create it (mode 600, owned by the account running Docker) before continuing." >&2
    exit 1
fi
export CRONOS_KEYS_FILE_HOST

if [ -z "${GRAFANA_ADMIN_PASSWORD:-}" ]; then
    GRAFANA_ADMIN_PASSWORD="$(openssl rand -base64 18)"
    export GRAFANA_ADMIN_PASSWORD
    echo "GRAFANA_ADMIN_PASSWORD not set — generated one for this run:"
    echo "  $GRAFANA_ADMIN_PASSWORD"
    echo "Save it now (Grafana only applies it on the very first start; export"
    echo "GRAFANA_ADMIN_PASSWORD yourself next time to reuse an existing credential)."
    echo
fi

echo "Building and starting the production stack (detached) — SPRING_PROFILES_ACTIVE=prod."
echo "Secrets: $CRONOS_KEYS_FILE_HOST"
echo
docker compose -f docker-compose.prod.yml up -d --build

echo
echo "API: http://localhost:9191/api/v1"
echo "Grafana/Prometheus/Loki are also up — see the SECURITY note in docker-compose.prod.yml"
echo "about not exposing them publicly as-is."
echo
echo "Logs:   docker compose -f docker-compose.prod.yml logs -f app"
echo "Stop:   docker compose -f docker-compose.prod.yml down"
