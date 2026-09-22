#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

sudo docker compose --profile prod down --remove-orphans

echo "prod profile stopped"
