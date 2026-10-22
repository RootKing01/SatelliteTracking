import { test, expect } from '@playwright/test'
import { DEMO_USER, loginAndAssertAuthenticated, logoutButton } from './helpers/auth-helpers'

test.describe('Protected Content', () => {
  test('should access satellite globe after login', async ({ page }) => {
    await page.goto('/')
    await loginAndAssertAuthenticated(page, DEMO_USER.email, DEMO_USER.password)

    await expect(page.locator('section.viewer-section')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Satellite Tracker' })).toBeVisible()
  })

  test('should display user info in logout button', async ({ page }) => {
    await page.goto('/')
    // Login anche con lo username (non solo con l'email)
    await loginAndAssertAuthenticated(page, DEMO_USER.username, DEMO_USER.password)

    await expect(logoutButton(page)).toHaveText(`Logout (${DEMO_USER.username})`)
  })
})