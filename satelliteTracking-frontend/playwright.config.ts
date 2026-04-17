import { defineConfig, devices } from '@playwright/test'

/**
 * Environment-aware Playwright Configuration
 * Supports multiple deployment scenarios:
 * - dev-local: localhost:5173 (fast feedback, HMR enabled)
 * - dev-remote: LAN IP or domain (network testing)
 * - prod-local: localhost:5173 (built frontend)
 * - prod-remote: domain via HTTPS (final validation)
 */

// Get base URL from environment variable or default to dev-local
const baseURL = process.env.TEST_BASE_URL || 'http://localhost:5173'
const isHttps = baseURL.startsWith('https')
const timeout = process.env.TEST_TIMEOUT ? parseInt(process.env.TEST_TIMEOUT) : (isHttps ? 45000 : 30000)
const workers = process.env.TEST_WORKERS ? parseInt(process.env.TEST_WORKERS) : 1
const runAllBrowsers = process.env.TEST_ALL_BROWSERS === 'true'
const reuseExistingServer = process.env.TEST_REUSE_EXISTING_SERVER === 'true'
const isLocalDevTarget = /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(baseURL)

console.log(`Playwright Config:`)
console.log(`  Base URL: ${baseURL}`)
console.log(`  HTTPS: ${isHttps}`)
console.log(`  Timeout: ${timeout}ms`)
console.log(`  Workers: ${workers}`)
console.log(`  Cross-browser: ${runAllBrowsers}`)
console.log(`  Reuse existing dev server: ${reuseExistingServer}`)

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: workers,
  reporter: 'html',
  
  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    // Allow self-signed certificates in prod-remote scenarios
    ignoreHTTPSErrors: isHttps,
    // Longer timeout for network latency in remote scenarios
    navigationTimeout: timeout,
  },

  projects: runAllBrowsers
    ? [
        {
          name: 'chromium',
          use: { ...devices['Desktop Chrome'] },
        },
        {
          name: 'firefox',
          use: { ...devices['Desktop Firefox'] },
        },
        {
          name: 'webkit',
          use: { ...devices['Desktop Safari'] },
        },
      ]
    : [
        {
          name: 'chromium',
          use: { ...devices['Desktop Chrome'] },
        },
      ],

  webServer: 
    // Start Vite web server only for local HTTP targets.
    isLocalDevTarget
      ? {
          command: 'VITE_DEV_USE_HTTPS=false npm run dev -- --host 127.0.0.1 --port 5173 --strictPort',
          url: baseURL,
          reuseExistingServer,
          timeout: 120 * 1000,
        }
      : undefined,

  timeout: timeout,
  expect: {
    timeout: isHttps ? 10000 : 8000,
  },
})
