import { expect, type Page } from '@playwright/test'

// Utente creato all'avvio dal backend (profilo "test": app.auth.bootstrap.*).
export const DEMO_USER = {
  username: 'demo',
  email: 'demo@satellitetracker.local',
  password: 'Demo123!',
} as const

export const AUTH_COOKIE_NAME = 'st_auth'

// --- Locator condivisi (label/ruoli esatti: "Password" non coincide con "Conferma password") ---
export const loginUsernameOrEmail = (page: Page) => page.getByLabel('Username o email')
export const passwordField = (page: Page) => page.getByLabel('Password', { exact: true })
export const loginSubmitButton = (page: Page) => page.getByRole('button', { name: 'Accedi', exact: true })
export const registerSubmitButton = (page: Page) => page.getByRole('button', { name: 'Registrati', exact: true })
export const logoutButton = (page: Page) => page.getByRole('button', { name: /^Logout/ })

export async function loginAndAssertAuthenticated(page: Page, usernameOrEmail: string, password: string) {
  const authError = page.locator('.auth-error')

  await loginUsernameOrEmail(page).fill(usernameOrEmail)
  await passwordField(page).fill(password)

  const loginResponsePromise = page.waitForResponse(
    (response) =>
      response.url().includes('/api/auth/login') && response.request().method() === 'POST',
  )

  await loginSubmitButton(page).click()

  const loginResponse = await loginResponsePromise
  if (!loginResponse.ok()) {
    const body = await loginResponse.text()
    throw new Error(`Login API failed (${loginResponse.status()}): ${body}`)
  }

  if (await authError.isVisible()) {
    const errorText = (await authError.textContent())?.trim() || 'Errore autenticazione non disponibile'
    throw new Error(`Login did not produce authenticated UI state: ${errorText}`)
  }

  await expect(logoutButton(page)).toBeVisible({ timeout: 15000 })
}

export async function performLogoutAndAssertLoginVisible(page: Page) {
  const logout = logoutButton(page)
  const loginButton = loginSubmitButton(page)

  await expect(logout).toBeVisible({ timeout: 15000 })

  let logoutCompleted = false
  for (let attempt = 0; attempt < 2 && !logoutCompleted; attempt++) {
    try {
      await logout.click({ timeout: 5000 })
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