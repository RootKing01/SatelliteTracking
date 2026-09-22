import { test } from '@playwright/test'
import { loginAndAssertAuthenticated, performLogoutAndAssertLoginVisible } from './helpers/auth-helpers'

test.describe('Authentication Logout', () => {
  test('should logout and return to login', async ({ page }) => {
    await page.goto('/')
    await loginAndAssertAuthenticated(page, 'demo@satellitetracker.local', 'Demo123!')
    await performLogoutAndAssertLoginVisible(page)
  })
})
