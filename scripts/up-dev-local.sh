#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

sudo docker compose --profile prod down --remove-orphans || true
FRONTEND_DEV_BIND_ADDRESS=127.0.0.1 sudo docker compose --profile dev up -d --build

echo "dev-local ready on http://127.0.0.1:5173"
