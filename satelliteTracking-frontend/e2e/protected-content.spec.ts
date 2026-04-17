import { test, expect } from '@playwright/test'
import { loginAndAssertAuthenticated } from './helpers/auth-helpers'

test.describe('Protected Content', () => {
  test('should access satellite globe after login', async ({ page }) => {
    await page.goto('/')
    await loginAndAssertAuthenticated(page, 'demo@satellitetracker.local', 'Demo123!')

    await expect(page.locator('text=Live globe')).toBeVisible({ timeout: 10000 })
    await expect(page.locator('text=Satellite Tracker')).toBeVisible()
  })

  test('should display user info in logout button', async ({ page }) => {
    await page.goto('/')
    await loginAndAssertAuthenticated(page, 'demo', 'Demo123!')

    const logoutButton = page.locator('button:has-text("Logout")')
    await expect(logoutButton).toContainText(/Logout \(.+\)/)
  })
})
