#!/usr/bin/env bash
# Starts the local dev stack (docker-compose.yml): app with hot reload (docker compose watch),
# Redis, Mailpit, Loki/Promtail/Grafana/Prometheus. See docs/RUNBOOK.md for the full walkthrough.
#
# Usage: ./scripts/start-local.sh
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

if ! command -v docker >/dev/null 2>&1; then
    echo "docker is not installed or not on PATH. Install Docker Desktop and retry." >&2
    exit 1
fi
if ! docker compose version >/dev/null 2>&1; then
    echo "docker compose (v2, the 'docker compose' subcommand) is required. Update Docker Desktop." >&2
    exit 1
fi

if [ ! -f .env ]; then
    echo "No .env found — creating one from .env.example."
    cp .env.example .env
    echo
    echo "Edit .env now and fill in real values (DB_URL/DB_USERNAME/DB_PASSWORD for your Neon"
    echo "branch, JWT_SECRET, MAIL_PASSWORD), then re-run this script."
    echo "(Already have a keys.properties file for qa/prod? See docker-compose.keys-file.yml"
    echo "instead of filling in .env — docs/RUNBOOK.md explains both paths.)"
    exit 1
fi

echo "Starting local stack with hot reload — docker compose watch (Ctrl+C to stop)."
echo "API:        http://localhost:9191/api/v1"
echo "Mailpit:    http://localhost:8025"
echo "Grafana:    http://localhost:3000"
echo "Prometheus: http://localhost:9090"
echo
exec docker compose watch
