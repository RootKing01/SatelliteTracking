# Quick Test How-To

## Run All Backend Tests
```bash
cd satelliteTracking
./mvnw clean test
```

## Run All Frontend Tests
```bash
cd satelliteTracking-frontend
npm install
npm run test
```

## Run E2E Tests
```bash
cd satelliteTracking-frontend
npm install
npx playwright install
npm run test:e2e
```

## Check Test Coverage
```bash
# Backend
cd satelliteTracking
./mvnw clean test jacoco:report
# Report: target/site/jacoco/index.html

# Frontend
cd satelliteTracking-frontend
npm run test:coverage
# Report: coverage/index.html
```

## Debug Tests
```bash
# Watch mode for quick feedback
npm run test -- --watch

# UI mode to see test runner
npm run test:ui

# E2E with visual inspector
npm run test:e2e:ui
```
