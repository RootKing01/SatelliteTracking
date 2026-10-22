import { test, expect } from '@playwright/test'
import {
  AUTH_COOKIE_NAME,
  DEMO_USER,
  loginAndAssertAuthenticated,
  loginSubmitButton,
  loginUsernameOrEmail,
  logoutButton,
  passwordField,
} from './helpers/auth-helpers'

test.describe('Authentication Login', () => {
  test('should login with valid credentials', async ({ page }) => {
    await page.goto('/')
    await loginAndAssertAuthenticated(page, DEMO_USER.email, DEMO_USER.password)

    const cookies = await page.context().cookies()
    const authCookie = cookies.find((c) => c.name === AUTH_COOKIE_NAME)

    expect(authCookie).toBeDefined()
    expect(authCookie?.httpOnly).toBe(true)
    expect(authCookie?.sameSite).toBe('Lax')
    await expect(page.locator('section.viewer-section')).toBeVisible()
  })

  test('should show viewer after login (stable UI check)', async ({ page }) => {
    // Blocca le dipendenze instabili (satelliti/globe/health)
    await page.route('**/api/satellites/groups-stats', (route) => {
      route.fulfill({
        status: 200,
        body: JSON.stringify({ stats: { stations: 1 }, total: 1 }),
        contentType: 'application/json',
      })
    })

    await page.route('**/api/satellites/positions**', (route) => {
      route.fulfill({
        status: 200,
        body: JSON.stringify([]),
        contentType: 'application/json',
      })
    })

    await page.route('**/api/system/health', (route) => {
      route.fulfill({
        status: 200,
        body: JSON.stringify({
          status: 'UP',
          checkedAt: '2026-10-11T12:00:00Z',
          components: { api: 'UP', database: 'UP', orekit: 'UP' },
          orekitDataPath: '/orekit-data',
        }),
        contentType: 'application/json',
      })
    })

    await page.goto('/')
    await loginAndAssertAuthenticated(page, DEMO_USER.email, DEMO_USER.password)

    // Verità lato backend
    await expect
      .poll(async () => {
        const res = await page.request.get('/api/auth/me')
        return (await res.json()).authenticated
      })
      .toBe(true)

    // Verità lato UI: shell dell'app (non la schermata di login) con l'utente corretto
    await expect(page.locator('.app-shell')).toBeVisible()
    await expect(logoutButton(page)).toHaveText(`Logout (${DEMO_USER.username})`)
  })

  test('should show error on invalid login credentials', async ({ page }) => {
    await page.goto('/')

    await loginUsernameOrEmail(page).fill(DEMO_USER.email)
    await passwordField(page).fill('WrongPassword123')

    const loginResponsePromise = page.waitForResponse(
      (response) =>
        response.url().includes('/api/auth/login') && response.request().method() === 'POST',
    )
    await loginSubmitButton(page).click()

    // Lo status è deterministico; il testo dell'errore dipende da come il backend espone il body.
    const loginResponse = await loginResponsePromise
    expect(loginResponse.status()).toBe(401)

    await expect(page.locator('.auth-error')).toBeVisible({ timeout: 5000 })
    await expect(page.locator('.auth-error')).toHaveText(/\S/)

    // Nessuna sessione creata
    await expect(logoutButton(page)).toHaveCount(0)
    const cookies = await page.context().cookies()
    expect(cookies.find((c) => c.name === AUTH_COOKIE_NAME)).toBeUndefined()
  })
})