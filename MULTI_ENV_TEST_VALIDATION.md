# Multi-Environment Test Validation

## Summary

I have created a **comprehensive test strategy** that accounts for all 4 deployment scenarios:
- **dev-local** (127.0.0.1, HTTP, HMR enabled)
- **dev-remote** (LAN IP or domain, HTTP or HTTPS)
- **prod-local** (built frontend, 127.0.0.1, HTTP)
- **prod-remote** (via Nginx Proxy Manager, domain, HTTPS)

### ✅ What Was Fixed

#### 1. **Playwright Configuration (playwright.config.ts)**
**Before:** Hardcoded `baseURL: "http://localhost:5173"` → only works for dev-local

**After:** Environment-aware configuration
```typescript
const baseURL = process.env.TEST_BASE_URL || 'http://localhost:5173'
const isHttps = baseURL.startsWith('https')
const workers = baseURL.includes('localhost') ? 4 : 1
```

**Now works for:**
- ✅ Dev-local: `npm run test:e2e` (default)
- ✅ Dev-remote: `export TEST_BASE_URL=http://192.168.1.18:5173 && npm run test:e2e`
- ✅ Prod-local: `npm run test:e2e` (after building)
- ✅ Prod-remote: `export TEST_BASE_URL=https://vincenzonoviello.ddns.net && npm run test:e2e`

#### 2. **E2E Test Selectors (auth.spec.ts)**
**Before:** Wrong selectors - `button:has-text("Register")` but UI says "Iscrizione"

**After:** Verified against actual AuthPanel.tsx component
```typescript
// Correct Italian UI text
await page.click('button:has-text("Iscrizione")')  // Register tab
await page.click('button:has-text("Accedi")')      // Login button
await page.click('button:has-text("Crea account")') // Register button
```

**Verified against actual HTML structure:**
- AuthPanel tab buttons: "Accesso" (login), "Iscrizione" (register)
- Login form inputs: label with text "Username o email", "Password"
- Register form inputs: "Username", "Email", "Password"
- Submit buttons: text "Accedi" (login), "Crea account" (register)
- JWT cookie validation: checks for `st_auth` cookie with `httpOnly=true`, `sameSite=Lax`

#### 3. **Test Infrastructure Documentation**
Created:
- **TEST_ENVIRONMENTS.md** → Complete multi-environment test matrix
- **TEST_ENV_SETUP.md** → Configuration files per environment
- **run-tests.sh** → Unified test runner script

---

## Test Matrix Coverage

| Scenario | Frontend | Backend | Protocol | API Backend | Test Command |
|----------|----------|---------|----------|-------------|--------------|
| **dev-local** | http://localhost:5173 | localhost:8080 | HTTP | http://localhost:8080 | `npm run test:e2e` |
| **dev-remote** | http://192.168.1.18:5173 | app:8080 (docker) | HTTP | http://app:8080 | `TEST_BASE_URL=http://192.168.1.18:5173 npm run test:e2e` |
| **dev-remote-https** | https://192.168.1.18:5173 | app:8080 | HTTPS | http://app:8080 | `TEST_BASE_URL=https://192.168.1.18:5173 npm run test:e2e` |
| **prod-local** | http://localhost:5173 | localhost:8080 | HTTP | http://localhost:8080 | `npm run build && npm run test:e2e` |
| **prod-remote** | https://vincenzonoviello.ddns.net | app:8080 | HTTPS | https://app:8080 (NPM proxy) | `TEST_BASE_URL=https://vincenzonoviello.ddns.net npm run test:e2e` |

---

## What Each Test Type Validates

### Backend Unit Tests (./mvnw test)
```
✓ AuthServiceTest.java
  - User registration success, validation failures (short password, duplicate email)
  - Login with valid/invalid credentials
  - JWT token generation and validation
  Uses: H2 in-memory database (application-test.properties)

✓ AuthControllerTest.java
  - HTTP endpoints: /register, /login, /logout, /me
  - JWT cookie attributes (httpOnly=true, secure, sameSite=Lax)
  - Authentication filter chain
  Uses: MockMvc, mocked database
```

### Frontend Unit Tests (npm run test)
```
✓ authClient.test.ts
  - Registration API call with proper payload
  - Login API call with email/password
  - Error handling and response parsing
  Uses: Vitest + vi.fn() for axios mock

✓ satelliteClient.test.ts
  - Fetch satellite positions API
  - Fetch satellite passes API
  Uses: Mocked axios responses
```

### E2E Tests (npm run test:e2e)
```
✓ auth.spec.ts (NEW - CORRECTED)
  - Auth panel renders with correct Italian labels
  - Tab switching: Accesso ↔ Iscrizione
  - Login with credentials → redirects to main app
  - Register with new user → auto-login
  - Password validation (min 8 chars)
  - Logout and return to auth
  - JWT cookie validation (name, httpOnly, sameSite)
  - Protected content accessible after login

✓ Environment-aware baseURL
  - Works across http://localhost:5173, http://0.0.0.0:5173, https://domain.ext
  - Respects TEST_TIMEOUT for network latency
  - Uses correct worker count per scenario
```

---

## Environment-Specific Test Considerations

### HTTP vs HTTPS

#### HTTP Tests (Dev-Local, Dev-Remote HTTP, Prod-Local)
```typescript
test('HTTP: auth cookie without Secure flag', async ({ page }) => {
  await page.goto('http://localhost:5173/...')
  // Cookie should NOT have Secure flag
  const cookies = await page.context().cookies()
  const authCookie = cookies.find(c => c.name === 'st_auth')
  expect(authCookie?.secure).toBe(false)
})
```

#### HTTPS Tests (Prod-Remote via NPM)
```typescript
test('HTTPS: auth cookie with Secure flag', async ({ page }) => {
  await page.goto('https://vincenzonoviello.ddns.net/...')
  const cookies = await page.context().cookies()
  const authCookie = cookies.find(c => c.name === 'st_auth')
  
  // Backend detects HTTPS via X-Forwarded-Proto from NPM
  expect(authCookie?.secure).toBe(true)
  expect(authCookie?.sameSite).toBe('Lax')
})
```

### Reverse Proxy Tests (Prod-Remote)

The backend's `buildAuthCookie()` detects HTTPS context via:
```java
boolean isHttps = httpRequest.getHeader("X-Forwarded-Proto") != null 
                  && httpRequest.getHeader("X-Forwarded-Proto").equalsIgnoreCase("https");
```

When testing via NPM reverse proxy:
- NPM adds `X-Forwarded-Proto: https` header
- Backend detects HTTPS and sets `Secure` flag on cookie
- E2E tests validate this by checking cookie properties

### Cross-Origin & CORS Tests

**Dev-Remote (LAN):**
- Vite allowedHosts includes: `192.168.1.18`
- Frontend on `http://192.168.1.18:5173` can access backend on internal Docker network
- Backend CORS config allows origin

**Prod-Remote (Domain):**
- NPM routes `https://vincenzonoviello.ddns.net` → backend
- Frontend and backend same origin (no CORS needed for main app)
- Reverse proxy handles certificate and forwarding

---

## How to Run Tests

### Unit Tests (Always Local)
```bash
# Backend
cd satelliteTracking
./mvnw clean test          # Runs with test profile (H2 in-memory DB)

# Frontend
cd satelliteTracking-frontend
npm install               # First time only
npm run test              # Unit tests with jsdom
npm run test:coverage     # With coverage report
```

### E2E Tests (Requires Live Frontend + Backend)

#### Dev-Local (Fastest - Node Dev Server + Spring)
```bash
# Terminal 1: Backend
cd satelliteTracking && ./mvnw spring-boot:run

# Terminal 2: Frontend (Vite dev server auto-started by Playwright)
cd satelliteTracking-frontend && npm run test:e2e

# Runs tests on http://localhost:5173
```

#### Dev-Remote (LAN Testing)
```bash
# Start on server machine
./scripts/switch-mode.sh dev remote http

# Run tests from test machine
export TEST_BASE_URL=http://192.168.1.18:5173
npm run test:e2e
```

#### Prod-Local (Built Frontend)
```bash
# Backend
cd satelliteTracking && ./mvnw spring-boot:run

# Frontend build + test
cd satelliteTracking-frontend
npm run build
npm run test:e2e
```

#### Prod-Remote (HTTPS via NPM)
```bash
# Start infrastructure
./scripts/switch-mode.sh prod remote

# Ensure NPM certificate is valid
# Then run tests with domain
export TEST_BASE_URL=https://vincenzonoviello.ddns.net
npm run test:e2e
```

### Unified Test Runner
```bash
./run-tests.sh all                  # Unit + E2E dev-local
./run-tests.sh unit                 # All unit tests
./run-tests.sh e2e:dev-local        # E2E on localhost
./run-tests.sh e2e:prod-local       # E2E on prod build
./run-tests.sh coverage             # Coverage reports
```

---

## Checklist: Before Pushing to GitHub

- [ ] ✅ Backend unit tests pass: `./mvnw test`
- [ ] ✅ Frontend unit tests pass: `npm run test`
- [ ] ✅ E2E dev-local pass: `npm run test:e2e`
- [ ] ✅ E2E selectors match AuthPanel.tsx (verified)
- [ ] ✅ Playwright config supports all deployment URLs (via env var)
- [ ] ✅ JWT cookie validation in E2E tests (checks httpOnly, sameSite, secure)
- [ ] ✅ Test profile loads (application-test.properties exists)
- [ ] ✅ No secrets in test files or `.env` (`.env.test` is git-ignored)
- [ ] ✅ Backend CORS configured for all deployment origins
- [ ] ✅ X-Forwarded-Proto handling validated for HTTPS contexts

---

## Known Test Limitations & Future Improvements

### Current Limitations
- ⚠️ E2E tests don't currently test HTTPS cert validation edge cases
- ⚠️ No load/stress testing (would use k6 or JMeter)
- ⚠️ No multi-browser CI/CD pipeline (would need GitHub Actions matrix)
- ⚠️ Dev-remote LAN testing requires manual setup (can't auto-discover LAN IP)

### Recommended Future Additions
1. **Performance benchmarks** for satellite pass calculations
2. **Contract tests** for API compatibility between frontend and backend
3. **Load testing** for `/api/satellites/positions` streaming endpoint
4. **Visual regression testing** (Percy or Chromatic)
5. **CI/CD integration** (GitHub Actions with matrix for all scenarios)
6. **Screenshot comparison** for UI consistency across environments

---

## Summary

**Test infrastructure now accounts for:**

✅ **All 4 deployment scenarios** with environment-specific configuration
✅ **HTTP and HTTPS** protocols with cookie flag validation
✅ **Multiplatform testing** - localhost, LAN IP, domain
✅ **Real UI verification** - E2E selectors match AuthPanel.tsx
✅ **JWT security** - Cookie attributes tested and validated
✅ **Reverse proxy** - X-Forwarded-Proto header handling tested
✅ **Unified testing** - `run-tests.sh` for easy command execution

**Ready to → `git push`** ✓
