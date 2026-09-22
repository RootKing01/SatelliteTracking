import { expect, type Page } from '@playwright/test'

export async function loginAndAssertAuthenticated(page: Page, usernameOrEmail: string, password: string) {
  const emailInput = page.locator('label:has-text("Username o email") input')
  const passwordInput = page.locator('label:has-text("Password") input').first()
  const authError = page.locator('.auth-error')
  const logoutButton = page.locator('button:has-text("Logout")')

  await emailInput.fill(usernameOrEmail)
  await passwordInput.fill(password)

  const loginResponsePromise = page.waitForResponse(
    (response) =>
      response.url().includes('/api/auth/login') && response.request().method() === 'POST',
  )

  await page.click('button:has-text("Accedi")')

  const loginResponse = await loginResponsePromise
  if (!loginResponse.ok()) {
    const body = await loginResponse.text()
    throw new Error(`Login API failed (${loginResponse.status()}): ${body}`)
  }

  if (await authError.isVisible()) {
    const errorText = (await authError.textContent())?.trim() || 'Errore autenticazione non disponibile'
    throw new Error(`Login did not produce authenticated UI state: ${errorText}`)
  }

  await expect(logoutButton).toBeVisible({ timeout: 15000 })
}

export async function performLogoutAndAssertLoginVisible(page: Page) {
  const logoutButton = page.locator('button:has-text("Logout")')
  const loginButton = page.locator('button:has-text("Accedi")')

  await expect(logoutButton).toBeVisible({ timeout: 15000 })

  let logoutCompleted = false
  for (let attempt = 0; attempt < 2 && !logoutCompleted; attempt++) {
    try {
      await logoutButton.click({ timeout: 5000 })
      logoutCompleted = true
    } catch (error) {
      if (await loginButton.isVisible()) {
        logoutCompleted = true
      } else if (attempt === 1) {
        throw error
      }
    }
  }

  await expect(loginButton).toBeVisible({ timeout: 5000 })
}
