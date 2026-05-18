#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

running="$(sudo docker ps --format '{{.Names}}')"

cleanup_stale_bindings() {
  echo "Cleaning stale Docker proxy listeners and common bound ports..."
  sudo pkill -f docker-proxy || true
  sudo pkill -f containerd-proxy || true

  if command -v fuser >/dev/null 2>&1; then
    sudo fuser -k 80/tcp || true
    sudo fuser -k 81/tcp || true
    sudo fuser -k 443/tcp || true
    sudo fuser -k 5173/tcp || true
  fi
}

stop_conflicting_port_publishers() {
  local container_ids
  container_ids="$(sudo docker ps -q --filter publish=80 --filter publish=81 --filter publish=443 --filter publish=5173 || true)"

  if [[ -n "$container_ids" ]]; then
    echo "Stopping containers still publishing tracked ports..."
    sudo docker stop $container_ids || true
    sudo docker rm -f $container_ids || true
  fi
}

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

stop_conflicting_port_publishers

cleanup_stale_bindings

echo "All satellite profiles stopped."
