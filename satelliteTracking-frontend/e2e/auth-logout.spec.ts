import { test, expect } from '@playwright/test'
import {
  AUTH_COOKIE_NAME,
  DEMO_USER,
  loginAndAssertAuthenticated,
  performLogoutAndAssertLoginVisible,
} from './helpers/auth-helpers'

test.describe('Authentication Logout', () => {
  test('should logout and return to login', async ({ page }) => {
    await page.goto('/')
    await loginAndAssertAuthenticated(page, DEMO_USER.email, DEMO_USER.password)
    await performLogoutAndAssertLoginVisible(page)

    // Il backend non riconosce più la sessione...
    const meResponse = await page.request.get('/api/auth/me')
    expect((await meResponse.json()).authenticated).toBe(false)

    // ...e il cookie di autenticazione è stato rimosso.
    const cookies = await page.context().cookies()
    expect(cookies.find((c) => c.name === AUTH_COOKIE_NAME)).toBeUndefined()
  })
})