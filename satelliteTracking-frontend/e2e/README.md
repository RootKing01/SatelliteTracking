# Playwright E2E Test Configuration

Install Playwright:
```bash
npm install -D @playwright/test
npx playwright install
```

Run tests:
```bash
npm run test:e2e
```

Run tests with UI:
```bash
npm run test:e2e:ui
```

Run tests in headed mode:
```bash
npm run test:e2e:headed
```

## Test Structure

- `auth-panel.spec.ts` - Rendering/auth tab navigation
- `auth-login.spec.ts` - Login flows and cookie validation
- `auth-register.spec.ts` - Registration flows and validation
- `auth-logout.spec.ts` - Logout behavior
- `protected-content.spec.ts` - Protected app content after login
- `helpers/auth-helpers.ts` - Shared helper functions for auth assertions
- API endpoints must be accessible
- Tests use localhost by default (update as needed)
