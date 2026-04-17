# Test Details (Single Source)

Questo file e il riferimento unico per eseguire e capire i test del progetto.

## Obiettivo

Coprire in modo coerente:
- Backend unit/integration test
- Frontend unit test
- E2E test autenticazione e contenuti protetti
- Scenari multi-ambiente (dev/prod, local/remote)

## Comandi principali

```bash
# Tutti i test standard (backend + frontend + e2e dev-local)
./run-tests.sh all

# Solo backend
./run-tests.sh unit:backend

# Solo frontend
./run-tests.sh unit:frontend

# E2E locale
./run-tests.sh e2e:dev-local

# Coverage
./run-tests.sh coverage
```

## Backend tests

Path: `satelliteTracking/src/test/java/com/satelliteTracking`

Suite principali:
- `AuthServiceTest`
- `AuthControllerTest`
- `PassTimeServiceTest`
- `PassPhotometryServiceTest`
- `SatelliteTrackerApplicationTests`

Profilo test:
- `satelliteTracking/src/test/resources/application-test.properties`
- DB H2 in-memory
- Scheduler disabilitato in test

Comando diretto:

```bash
cd satelliteTracking
./mvnw clean test
```

## Frontend unit tests

Path: `satelliteTracking-frontend/src/test`

Suite principali:
- `authClient.test.ts`
- `satelliteClient.test.ts`

Comando diretto:

```bash
cd satelliteTracking-frontend
npm run test
```

## E2E tests

Path: `satelliteTracking-frontend/e2e`

Suite principali:
- `auth-panel.spec.ts`
- `auth-login.spec.ts`
- `auth-register.spec.ts`
- `auth-logout.spec.ts`
- `protected-content.spec.ts`

Config:
- `satelliteTracking-frontend/playwright.config.ts`
- base URL tramite `TEST_BASE_URL` (default `http://localhost:5173`)

Comandi diretti:

```bash
cd satelliteTracking-frontend
npm run test:e2e
npm run test:e2e:ui
npm run test:e2e:headed
```

## Scenari ambiente supportati

- `dev-local`: frontend `localhost:5173`, backend locale
- `dev-remote`: frontend su IP LAN, protocollo http/https
- `prod-local`: frontend buildata in locale
- `prod-remote`: dominio con reverse proxy

Esempio E2E remoto:

```bash
export TEST_BASE_URL=https://vincenzonoviello.ddns.net
cd satelliteTracking-frontend
npm run test:e2e
```

## Pre-check prima di push

```bash
./run-tests.sh unit
./run-tests.sh e2e:dev-local
```

Se i test non partono, verifica prima:
- Versione Java compatibile (>=17)
- Permessi `node_modules/.vite-temp`
- Servizi Docker attivi nel profilo corretto
