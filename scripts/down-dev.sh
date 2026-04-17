#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

sudo docker compose --profile dev down --remove-orphans

echo "dev profile stopped"
