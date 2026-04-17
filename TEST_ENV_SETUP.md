# Test Environment Configuration Files

## Files to Create

### 1. `.env.test` (Git-ignored, local only)
```bash
# Frontend E2E Base URL - change per environment
TEST_BASE_URL=http://localhost:5173

# Backend API URL
TEST_API_URL=http://localhost:8080

# HTTPS enabled (affects cookie Secure flag validation)
TEST_HTTPS_ENABLED=false

# Timeout for network requests
TEST_TIMEOUT=5000

# Test credentials (if using bootstrap user)
TEST_USER_EMAIL=demo@satellitetracker.local
TEST_USER_PASSWORD=Demo123!

# Video/Screenshot capture
TEST_CAPTURE_FAILURES=true
TEST_VIDEO=retain-on-failure

# Number of parallel workers
TEST_WORKERS=1

# Debug mode
TEST_DEBUG=false
```

### 2. `playwright.config.dev-local.ts`
```typescript
import { defineConfig } from '@playwright/test'

export default defineConfig({
  use: {
    baseURL: 'http://localhost:5173',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  timeout: 5000,
  expect: { timeout: 2000 },
  workers: 4,  // Fast local testing
})
```

### 3. `playwright.config.prod-remote.ts`
```typescript
import { defineConfig } from '@playwright/test'

export default defineConfig({
  use: {
    baseURL: 'https://vincenzonoviello.ddns.net',
    screenshot: 'only-on-failure',
    video: 'on-first-retry',
    ignoreHTTPSErrors: true,  // Allow self-signed certs
  },
  timeout: 10000,  // Longer timeout for network latency
  expect: { timeout: 3000 },
  workers: 1,  // Serial execution for prod
})
```

### 4. `vitest.config.dev.ts`
```typescript
import { defineConfig } from 'vitest/config'
import viteConfig from './vite.config'

export default defineConfig({
  ...viteConfig,
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    testTimeout: 5000,
  }
})
```

## How to Use

### Local Development (Dev-Local)
```bash
npm run test              # Unit tests
npm run test:e2e        # E2E on localhost (default)
```

### Test on LAN (Dev-Remote)
```bash
# From server
./scripts/switch-mode.sh dev remote http

# From test client
export TEST_BASE_URL=http://192.168.1.18:5173
npm run test:e2e
```

### Production Build (Prod-Local)
```bash
npm run build
./scripts/switch-mode.sh prod local
npm run test:e2e
```

### Production Remote (HTTPS)
```bash
export TEST_BASE_URL=https://vincenzonoviello.ddns.net
npx playwright test --config playwright.config.prod-remote.ts
```
