import { defineConfig, devices } from '@playwright/test'

const baseURL = 'https://localhost:5173'
const backendURL = 'http://127.0.0.1:18080'

export default defineConfig({
  testDir: './e2e',

  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,

  reporter: 'html',

  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    ignoreHTTPSErrors: true,
    navigationTimeout: 30000,
  },

  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
      },
    },
  ],

 webServer: [
  {
    command:
      'mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=test -Dspring-boot.run.useTestClasspath=true "-Dspring-boot.run.arguments=--server.port=18080"',
    cwd: '../satelliteTracking',
    url: `${backendURL}/api/auth/me`,
    timeout: 120 * 1000,
    reuseExistingServer: false,
    env: {
      JAVA_HOME: 'C:\\Program Files\\Java\\jdk-17',
    },
  },
  {
    command: 'npm run dev',
    url: baseURL,
    timeout: 120 * 1000,
    reuseExistingServer: false,
    ignoreHTTPSErrors: true,
    env: {
      VITE_DEV_PROXY_TARGET: backendURL,
      VITE_DEV_USE_HTTPS: 'true',
    },
  },
],

  timeout: 30000,

  expect: {
    timeout: 8000,
  },
})