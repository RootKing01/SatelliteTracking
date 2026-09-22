#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

sudo docker compose --profile dev down --remove-orphans || true
FRONTEND_BIND_ADDRESS=0.0.0.0 sudo docker compose --profile prod --profile proxy up -d --build

echo "prod-remote ready on http://<server-ip>:5173 (or via reverse proxy HTTPS)"
echo "Nginx Proxy Manager UI: http://<server-ip>:81"
