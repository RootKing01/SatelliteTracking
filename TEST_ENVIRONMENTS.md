# Multi-Environment Test Configuration

## Test Matrix by Environment

```
┌─────────────────────────────────────────────────────────────────┐
│ Environment │ Frontend URL │ Backend  │ Protocol │ Test Focus   │
├─────────────────────────────────────────────────────────────────┤
│ dev-local   │ localhost:5173│ localhost│ HTTP     │ Fast feedback│
│ dev-remote  │ 0.0.0.0:5173 │ internal │ HTTP*    │ LAN testing  │
│ prod-local  │ localhost:5173│ localhost│ HTTP     │ Build output │
│ prod-remote │ domain.ext   │ internal │ HTTPS    │ Live testing │
└─────────────────────────────────────────────────────────────────┘
* dev-remote can be HTTP or HTTPS (configurable)
```

## Environment-Specific Test Profiles

### 1. Dev-Local (Primary Development)
**Use:** `npm run test` (unit tests) + `npm run test:e2e` locally
- ✅ Fast execution
- ✅ Vite HMR enabled
- ✅ Browser DevTools available
- ✅ Direct localhost access
- ✅ No reverse proxy

**Backend:** Standalone Spring Boot on `localhost:8080`
```bash
./mvnw spring-boot:run
```

**Commands:**
```bash
npm run test                  # Unit tests
npm run test:e2e            # E2E on localhost:5173
npm run test:e2e:ui         # Visual test runner
npm run test:coverage       # Coverage report
```

---

### 2. Dev-Remote (Network/Team Testing)
**Use:** When testing from another machine on LAN
- ✅ Tests from other devices
- ✅ API CORS validation (need allowedHosts whitelist)
- ✅ Network latency simulation
- ❌ No HMR (frontend already running)

**Prerequisites:**
```bash
# From host running dev-remote
./scripts/switch-mode.sh dev remote http
# Or with HTTPS for SSL testing
./scripts/switch-mode.sh dev remote https
```

**From test machine on LAN:**
```bash
# Test via LAN IP (e.g., 192.168.1.18)
export TEST_BASE_URL=http://192.168.1.18:5173
npm run test:e2e
```

**Vite config validation:**
```typescript
// Vite must include LAN IP in allowedHosts
server: {
  allowedHosts: [
    'localhost',
    '127.0.0.1',
    '192.168.1.18',        // ✅ Your LAN IP
    'vincenzonoviello.ddns.net'
  ]
}
```

---

### 3. Prod-Local (Built Frontend Testing)
**Use:** Test production build locally before deployment
- ✅ Tests against optimized build
- ✅ No DevServer, pure static files
- ✅ Nginx configuration validated

**Build & Run:**
```bash
# Build production frontend
cd satelliteTracking-frontend
npm run build

# Start prod locally
./scripts/switch-mode.sh prod local

# Run E2E on prod build
npm run test:e2e
```

**Backend:** Spring Boot on `localhost:8080`
```bash
cd satelliteTracking && ./mvnw spring-boot:run
```

---

### 4. Prod-Remote (Live/Staging HTTP/HTTPS)
**Use:** Final testing before production
- ✅ HTTPS certificate validation
- ✅ Reverse proxy behavior (NPM)
- ✅ Cookie secure flag tested
- ✅ Domain-based testing

**Prerequisites:**
```bash
# Start prod remotely (exposed on LAN/domain)
./scripts/switch-mode.sh prod remote

# Start Nginx Proxy Manager separately
sudo docker run -d --name nginx-proxy ... \
  -p 80:80 -p 81:81 -p 443:443
```

**Testing:**
```bash
# Test via domain (with valid SSL certificate)
export TEST_BASE_URL=https://vincenzonoviello.ddns.net
npm run test:e2e

# Or via LAN IP (HTTP)
export TEST_BASE_URL=http://192.168.1.18:5173
npm run test:e2e
```

---

## Test Environment Variables

Create `.env.test` to control test behavior:

```bash
# Base URL for E2E tests
TEST_BASE_URL=http://localhost:5173

# Backend API endpoint
TEST_API_URL=http://localhost:8080

# HTTPS mode (controls SSL/Cookie secure flag validation)
TEST_HTTPS_ENABLED=false

# Timeout for API calls (prod-remote may need higher value)
TEST_TIMEOUT=5000

# Test credentials (if using test user)
TEST_USER_EMAIL=demo@satellitetracker.local
TEST_USER_PASSWORD=Demo123!

# Whether to capture screenshots/videos on failure
TEST_CAPTURE_FAILURES=true

# Number of parallel workers for E2E tests
TEST_WORKERS=1  # Set higher for prod-local/local testing
```

---

## Protocol-Specific Test Scenarios

### HTTP Tests (dev-local, dev-remote HTTP, prod-local)
```typescript
test('HTTP: auth without secure cookie', async ({ page }) => {
  // Cookie should NOT have Secure flag
  await page.goto('http://localhost:5173/login')
  // ... login ...
  const cookies = await page.context().cookies()
  const authCookie = cookies.find(c => c.name === 'st_auth')
  
  expect(authCookie?.secure).toBe(false)  // ✅ HTTP context
})
```

### HTTPS Tests (prod-remote with valid cert)
```typescript
test('HTTPS: auth with secure cookie', async ({ page }) => {
  // Cookie MUST have Secure flag in HTTPS context
  await page.goto('https://vincenzonoviello.ddns.net/login')
  // ... login ...
  const cookies = await page.context().cookies()
  const authCookie = cookies.find(c => c.name === 'st_auth')
  
  expect(authCookie?.secure).toBe(true)  // ✅ HTTPS context
})

test('HTTPS: SameSite attribute', async ({ page }) => {
  await page.goto('https://vincenzonoviello.ddns.net/login')
  // ... login ...
  const cookies = await page.context().cookies()
  const authCookie = cookies.find(c => c.name === 'st_auth')
  
  expect(authCookie?.sameSite).toBe('Lax')  // ✅ Verified in HTTPS
})
```

### Reverse Proxy Tests (prod-remote)
```typescript
test('Reverse proxy: domain resolution', async ({ page }) => {
  // Test that NPM reverse proxy correctly routes traffic
  await page.goto('https://vincenzonoviello.ddns.net')
  
  // Check that we get the frontend (not 502 error)
  expect(page.url()).toContain('vincenzonoviello.ddns.net')
  await expect(page.locator('text=Satellite Tracker')).toBeVisible()
})

test('Reverse proxy: X-Forwarded-* headers', async ({ page }) => {
  // Verify backend receives correct forwarded headers from NPM
  await page.goto('https://vincenzonoviello.ddns.net')
  await page.click('button:has-text("Login")')
  // ... check that backend correctly identifies HTTPS via X-Forwarded-Proto ...
})
```

---

## Backend Test Environment Matrix

### Unit Tests (Always run with test profile)
```bash
./mvnw test  # Always uses application-test.properties
```

**Tested in isolation:**
- Auth logic (password hashing, validation)
- JWT generation/validation
- DTO serialization
- Business logic (pass calculation, visibility)

**Mocked:**
- Database (H2 in-memory)
- External APIs (Celestrak, Telegram)
- HTTP requests

### Integration Tests (New - test real scenarios)
Create `satelliteTracking/src/test/java/com/satelliteTracking/integration/` for:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CookieSecurityIntegrationTest {
    @Test
    void testAuthCookieSecureFlag_HttpContext() {
        // Test that APP_SECURITY_JWT_COOKIE_SECURE=false works for local
        // HTTP request should set cookie without Secure flag
    }
    
    @Test
    void testAuthCookieSecureFlag_HttpsContext() {
        // Test that X-Forwarded-Proto=https makes cookie Secure
        // Even if APP_SECURITY_JWT_COOKIE_SECURE=true
    }
    
    @Test
    void testCorsAllowedOrigins_DevLocal() {
        // CORS should allow http://localhost:5173
    }
    
    @Test
    void testCorsAllowedOrigins_ProdRemote() {
        // CORS should allow https://vincenzonoviello.ddns.net
    }
}
```

---

## Running Full Test Suite

### 1. Unit Tests (All Environments)
```bash
# Backend
cd satelliteTracking
./mvnw clean test

# Frontend
cd satelliteTracking-frontend
npm run test
```

### 2. E2E Tests by Environment

**Dev-Local:**
```bash
./scripts/switch-mode.sh dev local
npm run test:e2e
```

**Dev-Remote (from same LAN):**
```bash
# Machine A (server)
./scripts/switch-mode.sh dev remote http

# Machine B (test client)
export TEST_BASE_URL=http://192.168.1.18:5173
npm run test:e2e
```

**Prod-Local:**
```bash
./scripts/switch-mode.sh prod local
npm run test:e2e
```

**Prod-Remote (HTTPS via NPM):**
```bash
./scripts/switch-mode.sh prod remote
# Ensure NPM is running and has issued valid certificate
export TEST_BASE_URL=https://vincenzonoviello.ddns.net
npm run test:e2e -- --headed  # Watch progress
```

---

## CI/CD Test Strategy

### GitHub Actions: Multi-Environment Testing

```yaml
name: Test All Environments
on: [push, pull_request]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - run: |
          cd satelliteTracking && ./mvnw clean test
          cd ../satelliteTracking-frontend && npm install && npm run test

  e2e-dev-local:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - run: |
          # Start backend
          cd satelliteTracking && ./mvnw spring-boot:run &
          sleep 10
          
          # Start frontend dev
          cd satelliteTracking-frontend
          npm install
          npm run dev &
          sleep 10
          
          # Run E2E tests with dev-local config
          npm run test:e2e

  e2e-prod-local:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - run: |
          # Start backend
          cd satelliteTracking && ./mvnw spring-boot:run &
          sleep 10
          
          # Build and serve frontend prod
          cd satelliteTracking-frontend
          npm install
          npm run build
          npx http-server dist &
          sleep 5
          
          export TEST_BASE_URL=http://localhost:8080
          npm run test:e2e
```

---

## Checklist Before Deployment

- [ ] ✅ Unit tests pass: `./mvnw test`
- [ ] ✅ Frontend tests pass: `npm run test`
- [ ] ✅ E2E dev-local pass: `npm run test:e2e`
- [ ] ✅ E2E prod-local pass on built frontend
- [ ] ✅ HTTPS certificate valid on prod domain
- [ ] ✅ JWT cookie has correct Secure flag for environment
- [ ] ✅ CORS origins correct in `.env` for active profile
- [ ] ✅ X-Forwarded-Proto handling works (dev/prod remote)
- [ ] ✅ allowedHosts includes all used hostnames
- [ ] ✅ No secrets in `.env` (should be in `.gitignore`)

---

## Known Limitations & Future Improvements

⚠️ **Current:**
- E2E tests hardcoded to `localhost:5173` (will add env var support)
- No cross-device LAN testing in CI/CD (requires manual testing)
- No HTTPS certificate generation in test mode
- Load testing not included

✅ **Recommended Additions:**
1. Environment-specific Playwright configs
2. Test credentials per environment from `.env.test`
3. SSL certificate mock for HTTPS E2E tests
4. Multi-browser testing (Chrome, Firefox, Safari)
5. Performance benchmarks for pass calculations
6. Load testing with k6 or Apache JMeter
