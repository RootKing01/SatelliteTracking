#!/bin/bash
# run-tests.sh - Unified test runner for all environments

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_header() {
    echo -e "${BLUE}==== $1 ====${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

ensure_java_toolchain() {
    # Prefer JDK 17 explicitly for this project.
    if [[ -x /usr/lib/jvm/java-17-openjdk-amd64/bin/javac ]]; then
        export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
        export PATH="$JAVA_HOME/bin:$PATH"
    fi

    if ! command -v java >/dev/null 2>&1 || ! command -v javac >/dev/null 2>&1; then
        print_error "JDK non trovato: installa openjdk-17-jdk (o un JDK con javac)."
        return 1
    fi

    local java_major
    java_major=$(java -version 2>&1 | head -n1 | sed -E 's/.*version "([0-9]+).*/\1/')
    if [[ -z "$java_major" || "$java_major" -lt 17 ]]; then
        print_error "Versione Java non compatibile (${java_major:-unknown}). Serve Java >= 17."
        return 1
    fi
}

ensure_frontend_dependencies() {
    if [[ -f package-lock.json ]]; then
        npm ci --include=dev > /dev/null 2>&1
    else
        npm install --include=dev > /dev/null 2>&1
    fi
}

ensure_playwright_browsers() {
    npx playwright install > /dev/null 2>&1 || {
        print_error "Installazione browser Playwright fallita. Esegui: cd satelliteTracking-frontend && npx playwright install"
        return 1
    }
}

publish_playwright_report_web() {
    if [[ "${PLAYWRIGHT_PUBLISH_REPORT_WEB:-true}" != "true" ]]; then
        return 0
    fi

    local report_host="${PLAYWRIGHT_REPORT_HOST:-0.0.0.0}"
    local report_port="${PLAYWRIGHT_REPORT_PORT:-9323}"
    local report_log="/tmp/satellite-playwright-report.log"
    local report_url="http://localhost:${report_port}"

    if [[ ! -d "$SCRIPT_DIR/satelliteTracking-frontend/playwright-report" ]]; then
        print_warning "Report Playwright non trovato: esegui prima almeno una suite E2E."
        return 0
    fi

    pkill -f "playwright show-report --host ${report_host} --port ${report_port}" > /dev/null 2>&1 || true

    (
        cd "$SCRIPT_DIR/satelliteTracking-frontend"
        nohup npx playwright show-report --host "$report_host" --port "$report_port" > "$report_log" 2>&1 &
    )

    # Best-effort browser open: works on desktop Linux, WSL, macOS and Windows shells.
    if [[ "${PLAYWRIGHT_OPEN_REPORT_BROWSER:-true}" == "true" ]]; then
        if command -v xdg-open >/dev/null 2>&1; then
            nohup xdg-open "$report_url" > /dev/null 2>&1 || true
        elif command -v sensible-browser >/dev/null 2>&1; then
            nohup sensible-browser "$report_url" > /dev/null 2>&1 || true
        elif command -v open >/dev/null 2>&1; then
            nohup open "$report_url" > /dev/null 2>&1 || true
        elif command -v start >/dev/null 2>&1; then
            nohup start "$report_url" > /dev/null 2>&1 || true
        fi
    fi

    print_success "Report Playwright disponibile su ${report_url}"
    print_warning "Per disabilitare il publish web: PLAYWRIGHT_PUBLISH_REPORT_WEB=false ./run-tests.sh e2e:dev-local"
    print_warning "Per non aprire automaticamente il browser: PLAYWRIGHT_OPEN_REPORT_BROWSER=false ./run-tests.sh e2e:dev-local"
}

TEMP_BACKEND_PID=""
TEMP_BACKEND_PORT=""
TEMP_BACKEND_PID_FILE="/tmp/satellite-backend-e2e.pid"
E2E_BACKEND_PORT_START=18080
E2E_BACKEND_PORT_END=18120

cleanup_stale_temp_backend() {
    if [[ -f "$TEMP_BACKEND_PID_FILE" ]]; then
        local stale_pid
        stale_pid=$(cat "$TEMP_BACKEND_PID_FILE" 2>/dev/null || true)
        if [[ -n "$stale_pid" ]] && kill -0 "$stale_pid" > /dev/null 2>&1; then
            print_warning "Rilevata istanza backend E2E precedente (PID ${stale_pid}), arresto forzato..."
            kill "$stale_pid" > /dev/null 2>&1 || true
            wait "$stale_pid" > /dev/null 2>&1 || true
        fi
        rm -f "$TEMP_BACKEND_PID_FILE"
    fi
}

cleanup_stale_frontend_dev_server() {
    local stale_frontend_pids
    stale_frontend_pids=$(fuser -n tcp 5173 2>/dev/null || true)
    if [[ -n "$stale_frontend_pids" ]]; then
        print_warning "Rilevata istanza frontend precedente sulla porta 5173, arresto in corso..."
        fuser -k 5173/tcp > /dev/null 2>&1 || true
    fi
}

cleanup_stale_e2e_backend_ports() {
    local found_stale=false
    local p

    for ((p=E2E_BACKEND_PORT_START; p<=E2E_BACKEND_PORT_END; p++)); do
        if fuser -n tcp "${p}" > /dev/null 2>&1; then
            if [[ "$found_stale" == false ]]; then
                print_warning "Rilevate istanze backend E2E residue nel range ${E2E_BACKEND_PORT_START}-${E2E_BACKEND_PORT_END}, arresto in corso..."
                found_stale=true
            fi
            fuser -k "${p}/tcp" > /dev/null 2>&1 || true
        fi
    done
}

find_free_backend_port() {
    local p
    for ((p=E2E_BACKEND_PORT_START; p<=E2E_BACKEND_PORT_END; p++)); do
        if ! ss -ltn | grep -q ":${p} "; then
            echo "$p"
            return 0
        fi
    done
    return 1
}

start_backend_for_e2e() {
    ensure_java_toolchain || return 1
    cleanup_stale_temp_backend
    cleanup_stale_e2e_backend_ports

    if [[ -n "${TEST_BACKEND_PORT:-}" ]]; then
        TEMP_BACKEND_PORT="$TEST_BACKEND_PORT"
        if ss -ltn | grep -q ":${TEMP_BACKEND_PORT} "; then
            print_error "Porta E2E backend ${TEMP_BACKEND_PORT} gia in uso. Imposta TEST_BACKEND_PORT con una porta libera."
            return 1
        fi
    else
        TEMP_BACKEND_PORT="$(find_free_backend_port)" || {
            print_error "Nessuna porta libera trovata nel range ${E2E_BACKEND_PORT_START}-${E2E_BACKEND_PORT_END} per backend E2E."
            return 1
        }
    fi

    print_warning "Avvio backend temporaneo E2E su porta ${TEMP_BACKEND_PORT} (profilo test)..."

    (
        cd "$SCRIPT_DIR/satelliteTracking"
        ./mvnw spring-boot:run -Dspring-boot.run.profiles=test -Dspring-boot.run.useTestClasspath=true -Dspring-boot.run.arguments=--server.port=${TEMP_BACKEND_PORT} > /tmp/satellite-backend-e2e.log 2>&1
    ) &
    TEMP_BACKEND_PID=$!
    echo "$TEMP_BACKEND_PID" > "$TEMP_BACKEND_PID_FILE"

    if ! timeout 120 bash -c "until curl -s http://localhost:${TEMP_BACKEND_PORT}/api/auth/me > /dev/null 2>&1; do :; done"; then
        print_error "Backend non pronto entro 120s. Vedi log: /tmp/satellite-backend-e2e.log"
        if [[ -n "$TEMP_BACKEND_PID" ]]; then
            kill "$TEMP_BACKEND_PID" > /dev/null 2>&1 || true
            TEMP_BACKEND_PID=""
        fi
        return 1
    fi

    print_success "Backend temporaneo pronto su porta ${TEMP_BACKEND_PORT}"
}

stop_temp_backend_if_started() {
    local stopped_any=false
    local backend_port_to_kill="$TEMP_BACKEND_PORT"

    if [[ -n "$TEMP_BACKEND_PID" ]]; then
        if kill -0 "$TEMP_BACKEND_PID" > /dev/null 2>&1; then
            kill "$TEMP_BACKEND_PID" > /dev/null 2>&1 || true
            wait "$TEMP_BACKEND_PID" > /dev/null 2>&1 || true
            stopped_any=true
        fi
    fi

    if [[ -f "$TEMP_BACKEND_PID_FILE" ]]; then
        local file_pid
        file_pid=$(cat "$TEMP_BACKEND_PID_FILE" 2>/dev/null || true)
        if [[ -n "$file_pid" ]] && [[ "$file_pid" != "$TEMP_BACKEND_PID" ]] && kill -0 "$file_pid" > /dev/null 2>&1; then
            kill "$file_pid" > /dev/null 2>&1 || true
            wait "$file_pid" > /dev/null 2>&1 || true
            stopped_any=true
        fi
        rm -f "$TEMP_BACKEND_PID_FILE"
    fi

    TEMP_BACKEND_PID=""
    TEMP_BACKEND_PORT=""

    if [[ "$stopped_any" == true ]]; then
        print_success "Backend temporaneo arrestato"
    fi

    if [[ -n "$backend_port_to_kill" ]] && fuser -n tcp "$backend_port_to_kill" > /dev/null 2>&1; then
        fuser -k "${backend_port_to_kill}/tcp" > /dev/null 2>&1 || true
    fi
}

show_usage() {
    cat <<EOF
Usage: ./run-tests.sh <command> [options]

Commands:
  all                 Run all tests (unit + E2E on dev-local)
  unit                Run unit tests only (backend + frontend)
  unit:backend        Run backend unit tests only
  unit:frontend       Run frontend unit tests only
  e2e                 Run E2E tests on current environment
  e2e:dev-local       Run E2E on local dev server
  e2e:prod-local      Run E2E on production build
  e2e:dev-remote      Run E2E on dev remote (via LAN IP)
  coverage            Generate coverage reports
  help                Show this help

Examples:
  ./run-tests.sh all
  ./run-tests.sh unit:backend
  ./run-tests.sh e2e:dev-local
  ./run-tests.sh coverage
EOF
}

run_backend_unit_tests() {
    print_header "Running Backend Unit Tests"
    ensure_java_toolchain || return 1
    cd satelliteTracking
    ./mvnw clean test || return 1
    cd ..
    print_success "Backend unit tests passed"
}

run_frontend_unit_tests() {
    print_header "Running Frontend Unit Tests"
    cd satelliteTracking-frontend
    ensure_frontend_dependencies || return 1
    npm run test -- --run || return 1
    cd ..
    print_success "Frontend unit tests passed"
}

run_all_unit_tests() {
    run_backend_unit_tests || return 1
    run_frontend_unit_tests || return 1
}

run_e2e_dev_local() {
    print_header "Running E2E Tests (Dev-Local)"
    cd satelliteTracking-frontend
    ensure_frontend_dependencies || return 1
    ensure_playwright_browsers || return 1

    if [[ "${TEST_REUSE_EXISTING_SERVER:-false}" != "true" ]]; then
        cleanup_stale_frontend_dev_server
    fi

    start_backend_for_e2e || return 1
    
    # Keep Playwright and Vite on the same protocol/host to avoid webServer readiness timeouts.
    export VITE_DEV_USE_HTTPS=false
    export TEST_BASE_URL=http://localhost:5173
    export TEST_REUSE_EXISTING_SERVER=false
    export VITE_DEV_PROXY_TARGET=http://127.0.0.1:${TEMP_BACKEND_PORT}

    set +e
    npm run test:e2e
    local e2e_status=$?
    set -e

    stop_temp_backend_if_started
    publish_playwright_report_web

    if [[ $e2e_status -ne 0 ]]; then
        return $e2e_status
    fi

    print_success "E2E tests passed (dev-local)"
}

trap 'stop_temp_backend_if_started' EXIT INT TERM

run_e2e_prod_local() {
    print_header "Running E2E Tests (Prod-Local)"
    cd satelliteTracking-frontend
    ensure_frontend_dependencies || return 1
    ensure_playwright_browsers || return 1
    
    # Check if backend is running
    if ! curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        print_error "Backend not running on localhost:8080"
        return 1
    fi
    
    # Check if production frontend is available
    if ! curl -s http://localhost:5173 > /dev/null 2>&1; then
        print_error "Frontend not available on localhost:5173"
        print_warning "Build and start production with: npm run build && ./scripts/switch-mode.sh prod local"
        return 1
    fi
    
    export TEST_BASE_URL=http://localhost:5173
    npm run test:e2e || return 1
    print_success "E2E tests passed (prod-local)"
}

run_coverage() {
    print_header "Generating Coverage Reports"
    
    # Backend coverage
    print_header "Backend Coverage"
    ensure_java_toolchain || return 1
    cd satelliteTracking
    ./mvnw clean test jacoco:report || print_warning "Backend coverage generation failed"
    echo "Backend coverage: satelliteTracking/target/site/jacoco/index.html"
    cd ..
    
    # Frontend coverage
    print_header "Frontend Coverage"
    cd satelliteTracking-frontend
    ensure_frontend_dependencies || return 1
    npm run test:coverage || print_warning "Frontend coverage generation failed"
    echo "Frontend coverage: satelliteTracking-frontend/coverage/index.html"
    cd ..
    
    print_success "Coverage reports generated"
}

# Main logic
COMMAND=${1:-help}

case $COMMAND in
    all)
        run_all_unit_tests && run_e2e_dev_local
        ;;
    unit)
        run_all_unit_tests
        ;;
    unit:backend)
        run_backend_unit_tests
        ;;
    unit:frontend)
        run_frontend_unit_tests
        ;;
    e2e)
        run_e2e_dev_local
        ;;
    e2e:dev-local)
        run_e2e_dev_local
        ;;
    e2e:prod-local)
        run_e2e_prod_local
        ;;
    coverage)
        run_coverage
        ;;
    help)
        show_usage
        ;;
    *)
        print_error "Unknown command: $COMMAND"
        show_usage
        exit 1
        ;;
esac

if [ $? -eq 0 ]; then
    exit 0
else
    exit 1
fi
