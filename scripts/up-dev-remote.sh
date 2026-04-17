#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

protocol="${1:-}"

if [[ -n "$protocol" ]]; then
	protocol="${protocol,,}"
	if [[ "$protocol" != "http" && "$protocol" != "https" ]]; then
		echo "Usage: ./scripts/up-dev-remote.sh [http|https]"
		exit 1
	fi
fi

sudo docker compose --profile prod down --remove-orphans || true

if [[ "$protocol" == "https" ]]; then
	export FRONTEND_DEV_BIND_ADDRESS=0.0.0.0 VITE_DEV_USE_HTTPS=true
	sudo -E docker compose --profile dev --profile proxy up -d --build
	unset FRONTEND_DEV_BIND_ADDRESS VITE_DEV_USE_HTTPS
	echo "dev-remote ready on https://<server-ip>:5173"
elif [[ "$protocol" == "http" ]]; then
	export FRONTEND_DEV_BIND_ADDRESS=0.0.0.0 VITE_DEV_USE_HTTPS=false
	sudo -E docker compose --profile dev --profile proxy up -d --build
	unset FRONTEND_DEV_BIND_ADDRESS VITE_DEV_USE_HTTPS
	echo "dev-remote ready on http://<server-ip>:5173"
else
	export FRONTEND_DEV_BIND_ADDRESS=0.0.0.0
	sudo -E docker compose --profile dev --profile proxy up -d --build
	unset FRONTEND_DEV_BIND_ADDRESS
	echo "dev-remote ready using .env VITE_DEV_USE_HTTPS setting"
fi

echo "Nginx Proxy Manager UI: http://<server-ip>:81"
