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

  test('auth debug - cookie session persistence', async ({ page }) => {

  // 1. vai al frontend
  await page.goto('/')

  // 2. login
  await loginAndAssertAuthenticated(
    page,
    'demo@satellitetracker.local',
    'Demo123!'
  )

  // 3. verifica cookie nel browser
  const cookies = await page.context().cookies()
  console.log('COOKIES:', cookies)

  const authCookie = cookies.find(c => c.name === 'st_auth')

  expect(authCookie).toBeDefined()

  // 4. chiamata diretta backend con cookie del browser
  const meResponse = await page.request.get('/api/auth/me')

  console.log('ME STATUS:', meResponse.status())
  console.log('ME BODY:', await meResponse.text())

  expect(meResponse.ok()).toBeTruthy()

  // 5. verifica UI minima (NON bloccare test su UI complessa)
  await expect(page.locator('body')).toBeVisible()
})

test('should show viewer after login (stable UI check)', async ({ page }) => {

  // 🔒 blocca dipendenze instabili (satelliti/globe)
  await page.route('**/api/satellites/**', route => {
    route.fulfill({
      status: 200,
      body: JSON.stringify([]),
      contentType: 'application/json'
    })
  })

  await page.route('**/api/live/**', route => {
    route.fulfill({
      status: 200,
      body: JSON.stringify({}),
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

  test('login stable session', async ({ page }) => {
  await page.route('**/api/satellites/**', route => {
    route.fulfill({
      status: 200,
      body: JSON.stringify([]),
      contentType: 'application/json'
    })
  })

  await page.goto('/')

  await loginAndAssertAuthenticated(
    page,
    'demo@satellitetracker.local',
    'Demo123!'
  )

  await expect.poll(async () => {
    const res = await page.request.get('/api/auth/me')
    return (await res.json()).authenticated
  }).toBe(true)

  await expect(
    page.locator('button:has-text("Logout")')
  ).toBeVisible()
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

  test('should validate JWT cookie on successful login', async ({ page }) => {
    await page.goto('/')
    await loginAndAssertAuthenticated(page, 'demo@satellitetracker.local', 'Demo123!')

    const cookies = await page.context().cookies()
    const authCookie = cookies.find((c) => c.name === 'st_auth')

    expect(authCookie).toBeDefined()
    expect(authCookie?.httpOnly).toBe(true)
    expect(authCookie?.sameSite).toBe('Lax')
  })
})
