#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

usage() {
  cat <<'EOF'
Usage:
  ./scripts/switch-mode.sh <profile> <scope> [protocol]
  ./scripts/switch-mode.sh
  ./scripts/switch-mode.sh down

Arguments:
  profile  dev | prod
  scope    local | remote
  protocol http | https (only for dev remote)

Examples:
  ./scripts/switch-mode.sh dev local
  ./scripts/switch-mode.sh dev remote
  ./scripts/switch-mode.sh dev remote https
  ./scripts/switch-mode.sh dev remote http
  ./scripts/switch-mode.sh prod local
  ./scripts/switch-mode.sh prod remote
  ./scripts/switch-mode.sh down
EOF
}

do_down_all() {
  echo "Stopping dev profile (if running)..."
  sudo docker compose --profile dev down --remove-orphans || true
  echo "Stopping prod profile (if running)..."
  sudo docker compose --profile prod down --remove-orphans || true
  echo "Stopping proxy profile (if running)..."
  sudo docker compose --profile proxy down --remove-orphans || true
  echo "All profiles stopped."
}

pick_profile() {
  local value
  while true; do
    read -r -p "Profile to start [dev/prod]: " value
    case "${value,,}" in
      dev|prod)
        echo "${value,,}"
        return 0
        ;;
      *)
        echo "Please enter 'dev' or 'prod'."
        ;;
    esac
  done
}

pick_scope() {
  local value
  while true; do
    read -r -p "Scope [local/remote]: " value
    case "${value,,}" in
      local|remote)
        echo "${value,,}"
        return 0
        ;;
      *)
        echo "Please enter 'local' or 'remote'."
        ;;
    esac
  done
}

pick_protocol() {
  local value
  while true; do
    read -r -p "DEV remote protocol [https/http] (default https): " value
    value="${value,,}"
    if [[ -z "$value" ]]; then
      echo "https"
      return 0
    fi
    case "$value" in
      https|http)
        echo "$value"
        return 0
        ;;
      *)
        echo "Please enter 'https' or 'http'."
        ;;
    esac
  done
}

if [[ $# -eq 1 && "$1" == "down" ]]; then
  do_down_all
  exit 0
elif [[ $# -eq 0 ]]; then
  echo "Interactive mode"
  profile="$(pick_profile)"
  scope="$(pick_scope)"
  if [[ "$profile" == "dev" && "$scope" == "remote" ]]; then
    protocol="$(pick_protocol)"
  else
    protocol=""
  fi
elif [[ $# -eq 2 || $# -eq 3 ]]; then
  profile="$1"
  scope="$2"
  protocol="${3:-}"
else
  usage
  exit 1
fi

if [[ "$profile" != "dev" && "$profile" != "prod" ]]; then
  echo "Invalid profile: $profile"
  usage
  exit 1
fi

if [[ "$scope" != "local" && "$scope" != "remote" ]]; then
  echo "Invalid scope: $scope"
  usage
  exit 1
fi

if [[ -n "${protocol:-}" && "$protocol" != "http" && "$protocol" != "https" ]]; then
  echo "Invalid protocol: $protocol"
  usage
  exit 1
fi

if [[ -n "${protocol:-}" && ! ( "$profile" == "dev" && "$scope" == "remote" ) ]]; then
  echo "Protocol can only be specified for dev remote mode."
  usage
  exit 1
fi

if [[ "$profile" == "dev" ]]; then
  opposite="prod"
  if [[ "$scope" == "local" ]]; then
    bind_key="FRONTEND_DEV_BIND_ADDRESS"
    bind_val="127.0.0.1"
  else
    bind_key="FRONTEND_DEV_BIND_ADDRESS"
    bind_val="0.0.0.0"
  fi
else
  opposite="dev"
  if [[ "$scope" == "local" ]]; then
    bind_key="FRONTEND_BIND_ADDRESS"
    bind_val="127.0.0.1"
  else
    bind_key="FRONTEND_BIND_ADDRESS"
    bind_val="0.0.0.0"
  fi
fi

echo "Stopping opposite profile: $opposite"
sudo docker compose --profile "$opposite" down --remove-orphans || true

if [[ "$scope" == "local" ]]; then
  echo "Stopping proxy profile for local mode..."
  sudo docker compose --profile proxy down --remove-orphans || true
fi

echo "Starting profile: $profile ($scope)"
if [[ "$profile" == "dev" && "$scope" == "remote" && -n "${protocol:-}" ]]; then
  if [[ "$protocol" == "https" ]]; then
    export "$bind_key=$bind_val" VITE_DEV_USE_HTTPS=true
    sudo -E docker compose --profile "$profile" --profile proxy up -d --build
    unset VITE_DEV_USE_HTTPS
  else
    export "$bind_key=$bind_val" VITE_DEV_USE_HTTPS=false
    sudo -E docker compose --profile "$profile" --profile proxy up -d --build
    unset VITE_DEV_USE_HTTPS
  fi
else
  export "$bind_key=$bind_val"
  if [[ "$scope" == "remote" ]]; then
    sudo -E docker compose --profile "$profile" --profile proxy up -d --build
  else
    sudo -E docker compose --profile "$profile" up -d --build
  fi
fi

echo "Done: $profile-$scope"
if [[ "$scope" == "local" ]]; then
  echo "Endpoint: http://127.0.0.1:5173"
else
  if [[ "$profile" == "dev" && "$scope" == "remote" && "${protocol:-}" == "https" ]]; then
    echo "Endpoint: https://<server-ip>:5173"
  else
    echo "Endpoint: http://<server-ip>:5173"
  fi
  echo "NPM UI: http://<server-ip>:81"
fi
