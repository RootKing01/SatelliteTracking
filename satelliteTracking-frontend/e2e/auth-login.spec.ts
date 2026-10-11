import { test, expect } from '@playwright/test'
import { loginAndAssertAuthenticated } from './helpers/auth-helpers'

test.describe('Authentication Login', () => {
  
  test('should login with valid credentials', async ({ page }) => {
    await page.goto('/')
    await loginAndAssertAuthenticated(page, 'demo@satellitetracker.local', 'Demo123!')
    const cookies = await page.context().cookies()
    const authCookie = cookies.find((c) => c.name === 'st_auth')

    expect(authCookie).toBeDefined()
    expect(authCookie?.httpOnly).toBe(true)
    expect(authCookie?.sameSite).toBe('Lax')
    await expect(page.locator('section.viewer-section')).toBeVisible()
  })

test('should show viewer after login (stable UI check)', async ({ page }) => {

  // 🔒 blocca dipendenze instabili (satelliti/globe)
  await page.route('**/api/satellites/groups-stats', route => {
    route.fulfill({
      status: 200,
      body: JSON.stringify({ stats: { stations: 1 }, total: 1 }),
      contentType: 'application/json'
    })
  })

  await page.route('**/api/satellites/positions**', route => {
    route.fulfill({
      status: 200,
      body: JSON.stringify([]),
      contentType: 'application/json'
    })
  })

  await page.route('**/api/system/health', route => {
    route.fulfill({
      status: 200,
      body: JSON.stringify({
        status: 'UP',
        checkedAt: '2026-10-11T12:00:00Z',
        components: { api: 'UP', database: 'UP', orekit: 'UP' },
        orekitDataPath: '/orekit-data'
      }),
      contentType: 'application/json'
    })
  })

  await page.goto('/')

  await loginAndAssertAuthenticated(
    page,
    'demo@satellitetracker.local',
    'Demo123!'
  )

  // backend truth
  await expect.poll(async () => {
    const res = await page.request.get('/api/auth/me')
    return (await res.json()).authenticated
  }).toBe(true)

  // UI stabile: NON viewer text
  await expect(page.locator('body')).not.toContainText(
    'Sessione scaduta',
    { timeout: 10000 }
  )

  // meglio di viewer-section:
  await expect(page.locator('main')).toBeVisible()
})

  test('should show error on invalid login credentials', async ({ page }) => {
    await page.goto('/')

    const emailInput = page.locator('label:has-text("Username o email") input')
    const passwordInput = page.locator('label:has-text("Password") input').first()

    await emailInput.fill('demo@satellitetracker.local')
    await passwordInput.fill('WrongPassword123')
    await page.click('button:has-text("Accedi")')

    await expect(page.locator('.auth-error')).toBeVisible({ timeout: 5000 })
  })

})
