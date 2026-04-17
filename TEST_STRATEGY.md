# Test Strategy

## Backend Testing

### Unit Tests
- **Location:** `satelliteTracking/src/test/java/com/satelliteTracking`
- **Framework:** JUnit 5 + Mockito
- **Coverage:**
  - `AuthServiceTest` - Registration, login, password validation
  - `AuthControllerTest` - HTTP endpoints, JWT cookie handling
  - `SatellitePassControllerTest` - Pass prediction endpoints
  - `PassTimeServiceTest` - Pass calculations
  - `PassVisibilityServiceTest` - Visibility logic

### Integration Tests
- Full Spring context tests with Mockito for external services
- Database integration tests with `@DataJpaTest`
- Mock API responses for external services (Celestrak, Telegram)

### Running Backend Tests
```bash
cd satelliteTracking
./mvnw test

# Run specific test class
./mvnw test -Dtest=AuthServiceTest

# With coverage (requires Jacoco)
./mvnw clean test jacoco:report
```

### Test Profile
- Spring profile: `test` (see `application-test.properties`)
- Uses H2 in-memory database for tests
- No external API calls

---

## Frontend Testing

### Unit Tests
- **Location:** `satelliteTracking-frontend/src/test`
- **Framework:** Vitest + React Testing Library
- **Coverage:**
  - `authClient.test.ts` - Auth API interactions
  - `satelliteClient.test.ts` - Satellite data fetching
  - Component tests for UI components

### Running Frontend Tests
```bash
cd satelliteTracking-frontend

# Install dependencies first
npm install

# Run unit tests
npm run test

# Run with UI
npm run test:ui

# Coverage report
npm run test:coverage
```

---

## End-to-End Testing

### E2E Tests
- **Location:** `satelliteTracking-frontend/e2e`
- **Framework:** Playwright
- **Scope:**
  - Authentication flows (register, login, logout)
  - Satellite search and details
  - Cesium map visualization
  - Pass predictions and visibility

### Running E2E Tests
```bash
cd satelliteTracking-frontend

# Install Playwright
npx playwright install

# Run E2E tests
npm run test:e2e

# Run with UI (visual test runner)
npm run test:e2e:ui

# Run in headed mode (see browser)
npm run test:e2e:headed
```

### Prerequisites for E2E
- Frontend running on `http://localhost:5173`
- Backend running on `http://localhost:8080`
- Optional: Test user with credentials `demo@satellitetracker.local` / `Demo123!`

---

## Test Coverage Goals

| Module | Target | Current |
|--------|--------|---------|
| Auth (Backend) | 85% | To Add |
| Services | 80% | Partial |
| Controllers | 75% | Partial |
| Frontend Components | 70% | To Add |
| E2E Flows | 60% | To Add |

---

## Continuous Integration

### Recommended CI/CD Setup (GitHub Actions)
```yaml
name: Tests
on: [push, pull_request]
jobs:
  test-backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      - run: cd satelliteTracking && ./mvnw test
      
  test-frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
        with:
          node-version: '20'
      - run: cd satelliteTracking-frontend && npm install && npm run test
      
  test-e2e:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
        with:
          node-version: '20'
      - run: cd satelliteTracking-frontend && npm install && npm run build && npm run test:e2e
```

---

## Key Testing Practices

✅ **DO:**
- Write tests for new features before or after implementation (TDD)
- Test error cases and edge cases
- Mock external services (APIs, databases)
- Use meaningful test names that describe what is being tested
- Keep tests isolated and independent
- Test both happy paths and failure scenarios

❌ **DON'T:**
- Test implementation details, test behavior
- Create highly coupled tests that break with minor refactors
- Skip testing for "simple" code
- Mock everything (mock only dependencies, not the code under test)
- Have tests that depend on execution order

---

## Debugging Tests

### Backend
```bash
# Run tests with debug output
./mvnw test -X

# Run single test with debug
./mvnw test -Dtest=AuthServiceTest#testRegisterUserSuccess -X
```

### Frontend
```bash
# Run tests in watch mode
npm run test -- --watch

# Run with debug console
npm run test -- --inspect-brk
```

### E2E
```bash
# Debug mode with inspector
npm run test:e2e -- --debug

# Headed mode to watch execution
npm run test:e2e:headed
```

---

## Future Improvements

1. ✅ Add unit tests for Auth services
2. ⬜ Add component tests for frontend panels
3. ⬜ Add integration tests for satellite calculations
4. ⬜ Add performance tests for pass prediction
5. ⬜ Set up code coverage reporting in CI/CD
6. ⬜ Add contract tests for API consumers
7. ⬜ Add load tests for concurrent users
