#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

sudo docker compose --profile dev down --remove-orphans || true
FRONTEND_BIND_ADDRESS=127.0.0.1 sudo docker compose --profile prod up -d --build

echo "prod-local ready on http://127.0.0.1:5173"
