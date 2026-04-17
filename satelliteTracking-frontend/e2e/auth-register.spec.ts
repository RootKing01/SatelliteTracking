import { test, expect } from '@playwright/test'
import { loginAndAssertAuthenticated } from './helpers/auth-helpers'

test.describe('Authentication Register', () => {
  test('should register new user with valid data', async ({ page, browserName }, testInfo) => {
    await page.goto('/')
    await page.click('button:has-text("Iscrizione")')

    const uniqueSuffix = `${Date.now()}-${browserName}-${testInfo.workerIndex}-${Math.random().toString(36).slice(2, 8)}`
    const uniqueUsername = `testuser-${uniqueSuffix}`
    const uniqueEmail = `test-${uniqueSuffix}@example.com`
    const password = 'TestPassword123!'

    const usernameInput = page.locator('label:has-text("Username") input')
    const emailInput = page.locator('label:has-text("Email") input')
    const passwordInput = page.locator('label:has-text("Password") input').first()

    await usernameInput.fill(uniqueUsername)
    await emailInput.fill(uniqueEmail)
    await passwordInput.fill(password)

    const registerResponsePromise = page.waitForResponse(
      (response) =>
        response.url().includes('/api/auth/register') && response.request().method() === 'POST',
    )

    await page.click('button:has-text("Crea account")')

    const registerResponse = await registerResponsePromise
    if (!registerResponse.ok()) {
      const body = await registerResponse.text()
      throw new Error(`Register API failed (${registerResponse.status()}): ${body}`)
    }

    const logoutButton = page.locator('button:has-text("Logout")')
    const loginButton = page.locator('button:has-text("Accedi")')
    const authenticatedAfterRegister = await logoutButton.isVisible({ timeout: 5000 }).catch(() => false)

    if (!authenticatedAfterRegister && await loginButton.isVisible()) {
      await loginAndAssertAuthenticated(page, uniqueEmail, password)
    }

    await expect(logoutButton).toBeVisible({ timeout: 15000 })
    await expect(page.locator('text=Live globe')).toBeVisible()
  })

  test('should show validation error for short password', async ({ page }) => {
    await page.goto('/')
    await page.click('button:has-text("Iscrizione")')

    const usernameInput = page.locator('label:has-text("Username") input')
    const emailInput = page.locator('label:has-text("Email") input')
    const passwordInput = page.locator('label:has-text("Password") input').first()

    await usernameInput.fill(`user${Date.now()}`)
    await emailInput.fill(`test-${Date.now()}@example.com`)
    await passwordInput.fill('short')
    await page.click('button:has-text("Crea account")')

    await expect(page.locator('.auth-error')).toBeVisible({ timeout: 5000 })
  })
})
