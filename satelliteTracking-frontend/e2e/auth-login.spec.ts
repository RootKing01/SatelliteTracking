import { test, expect } from '@playwright/test'
import { loginAndAssertAuthenticated } from './helpers/auth-helpers'

test.describe('Authentication Login', () => {
  test('should login with valid credentials', async ({ page }) => {
    await page.goto('/')
    await loginAndAssertAuthenticated(page, 'demo@satellitetracker.local', 'Demo123!')
    await expect(page.locator('text=Live globe')).toBeVisible()
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
