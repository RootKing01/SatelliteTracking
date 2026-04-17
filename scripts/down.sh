#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

running="$(sudo docker ps --format '{{.Names}}')"

if echo "$running" | grep -q '^satellite-tracker-frontend-dev$'; then
  echo "Detected running mode: dev"
else
  echo "Detected running mode: dev not running"
fi

if echo "$running" | grep -q '^satellite-tracker-frontend$'; then
  echo "Detected running mode: prod"
else
  echo "Detected running mode: prod not running"
fi

echo "Stopping dev profile (if running)..."
sudo docker compose --profile dev down --remove-orphans || true

echo "Stopping prod profile (if running)..."
sudo docker compose --profile prod down --remove-orphans || true

echo "Stopping proxy profile (if running)..."
sudo docker compose --profile proxy down --remove-orphans || true

echo "All satellite profiles stopped."
