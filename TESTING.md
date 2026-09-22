# Testing Guide (Single Source of Truth)

Questo documento e il riferimento unico per testare il progetto in tutti gli ambienti.

## Scope

Copre:
- test backend (JUnit)
- test frontend unit (Vitest)
- test E2E (Playwright)
- esecuzione multi-ambiente (dev/prod, local/remote)

## Prerequisiti

- Java 17+
- Node.js 20+
- Docker + Docker Compose (per scenari containerizzati)
- dipendenze frontend installate in `satelliteTracking-frontend`

## Comandi Rapidi (consigliati)

Usa lo script unificato in root:

```bash
# Tutto: backend unit + frontend unit + e2e dev-local
./run-tests.sh all

# Solo unit test
./run-tests.sh unit
./run-tests.sh unit:backend
./run-tests.sh unit:frontend

# E2E
./run-tests.sh e2e
./run-tests.sh e2e:dev-local
./run-tests.sh e2e:dev-remote
./run-tests.sh e2e:prod-local

# Coverage
./run-tests.sh coverage
```

## Comandi Diretti per Stack

### Backend

```bash
cd satelliteTracking
./mvnw clean test
```

File test profile:
- `satelliteTracking/src/test/resources/application-test.properties`

### Frontend Unit

```bash
cd satelliteTracking-frontend
npm run test -- --run
```

### E2E

```bash
cd satelliteTracking-frontend
npx playwright install
npm run test:e2e
```

Varianti utili:

```bash
npm run test:e2e:ui
npm run test:e2e:headed
```

## Matrice Ambienti

### dev-local

- Frontend: `http://localhost:5173`
- Backend: locale (porta gestita dallo script)
- Obiettivo: feedback veloce durante sviluppo

```bash
./scripts/switch-mode.sh dev local
./run-tests.sh e2e:dev-local
```

### dev-remote

- Frontend esposto su IP LAN/server
- Protocollo opzionale http/https per dev remoto

```bash
./scripts/switch-mode.sh dev remote http
# oppure
./scripts/switch-mode.sh dev remote https

export TEST_BASE_URL=http://<server-ip>:5173
./run-tests.sh e2e:dev-remote
```

### prod-local

- Frontend buildato in modalita production
- Test della build locale

```bash
./scripts/switch-mode.sh prod local
./run-tests.sh e2e:prod-local
```

### prod-remote

- Accesso pubblico via reverse proxy (Nginx Proxy Manager)
- HTTPS terminato sul proxy

```bash
./scripts/switch-mode.sh prod remote
export TEST_BASE_URL=https://<tuo-dominio>
cd satelliteTracking-frontend
npm run test:e2e
```

Nota importante su NPM in prod:
- Browser -> NPM: HTTPS
- NPM -> frontend container: HTTP (upstream interno)

## Dove Sono i Test

- Backend: `satelliteTracking/src/test/java/com/satelliteTracking`
- Frontend unit: `satelliteTracking-frontend/src/test`
- E2E: `satelliteTracking-frontend/e2e`

## Variabili Utili

- `TEST_BASE_URL`: base URL E2E (default localhost)
- `TEST_REUSE_EXISTING_SERVER`: riuso server dev gia attivo
- `TEST_BACKEND_PORT`: porta backend E2E temporaneo
- `PLAYWRIGHT_PUBLISH_REPORT_WEB`: pubblica report Playwright (`true/false`)
- `PLAYWRIGHT_OPEN_REPORT_BROWSER`: apertura browser automatica report (`true/false`)

Esempio:

```bash
TEST_BASE_URL=https://<tuo-dominio> npm run test:e2e
```

## Checklist Pre-Push

```bash
./run-tests.sh unit
./run-tests.sh e2e:dev-local
```

Per rilascio remoto:

```bash
./scripts/switch-mode.sh prod remote
TEST_BASE_URL=https://<tuo-dominio> npm run test:e2e
```

## Troubleshooting

### E2E non parte o timeout

- verifica `TEST_BASE_URL`
- verifica che frontend sia raggiungibile dal client test
- verifica backend disponibile (script e2e avvia backend temporaneo in dev-local)

### Errori Playwright browser

```bash
cd satelliteTracking-frontend
npx playwright install
```

### Java non compatibile

- usa Java 17+
- verifica `java -version`

### 502 in prod remoto

- in NPM usa upstream interno HTTP verso frontend prod
- non impostare HTTPS come forward scheme verso il container frontend
