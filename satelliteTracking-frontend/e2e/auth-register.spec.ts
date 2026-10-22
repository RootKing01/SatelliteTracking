import { test, expect } from '@playwright/test'
import { logoutButton, passwordField, registerSubmitButton } from './helpers/auth-helpers'

function uniqueSuffix(browserName: string, workerIndex: number) {
  return `${Date.now()}-${browserName}-${workerIndex}-${Math.random().toString(36).slice(2, 8)}`
}

test.describe('Authentication Register', () => {
  test('should register new user with valid data', async ({ page, browserName }, testInfo) => {
    await page.goto('/')
    await page.getByRole('button', { name: 'Iscrizione', exact: true }).click()

    const suffix = uniqueSuffix(browserName, testInfo.workerIndex)
    const uniqueUsername = `testuser-${suffix}`
    const uniqueEmail = `test-${suffix}@example.com`
    const password = 'TestPassword123!'

    await page.getByLabel('Username', { exact: true }).fill(uniqueUsername)
    await page.getByLabel('Email', { exact: true }).fill(uniqueEmail)
    await passwordField(page).fill(password)
    await page.getByLabel('Conferma password').fill(password)

    const registerResponsePromise = page.waitForResponse(
      (response) =>
        response.url().includes('/api/auth/register') && response.request().method() === 'POST',
    )

    await registerSubmitButton(page).click()

    const registerResponse = await registerResponsePromise
    if (!registerResponse.ok()) {
      const body = await registerResponse.text()
      throw new Error(`Register API failed (${registerResponse.status()}): ${body}`)
    }

    // La registrazione effettua direttamente il login: nessun passaggio manuale.
    await expect(logoutButton(page)).toBeVisible({ timeout: 15000 })
    await expect(logoutButton(page)).toContainText(uniqueUsername)
    await expect(page.locator('section.viewer-section')).toBeVisible()
  })

  test('should show validation error for short password', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('button', { name: 'Iscrizione', exact: true }).click()

    await page.getByLabel('Username', { exact: true }).fill(`user${Date.now()}`)
    await page.getByLabel('Email', { exact: true }).fill(`test-${Date.now()}@example.com`)
    await passwordField(page).fill('short')
    await page.getByLabel('Conferma password').fill('short')

    const registerResponsePromise = page.waitForResponse(
      (response) =>
        response.url().includes('/api/auth/register') && response.request().method() === 'POST',
    )
    await registerSubmitButton(page).click()

    // La regola (min. 8 caratteri) è applicata dal backend: 400 Bad Request.
    const registerResponse = await registerResponsePromise
    expect(registerResponse.status()).toBe(400)

    await expect(page.locator('.auth-error')).toBeVisible({ timeout: 5000 })
    await expect(page.locator('.auth-error')).toHaveText(/\S/)
    await expect(logoutButton(page)).toHaveCount(0)
  })

  test('should require matching passwords on registration', async ({ page, browserName }, testInfo) => {
    let registerRequestSent = false
    page.on('request', (request) => {
      if (request.url().includes('/api/auth/register')) registerRequestSent = true
    })

    await page.goto('/')
    await page.getByRole('button', { name: 'Iscrizione', exact: true }).click()

    const suffix = uniqueSuffix(browserName, testInfo.workerIndex)

    await page.getByLabel('Username', { exact: true }).fill(`testuser-confirm-${suffix}`)
    await page.getByLabel('Email', { exact: true }).fill(`test-confirm-${suffix}@example.com`)
    await passwordField(page).fill('TestPassword123!')
    await page.getByLabel('Conferma password').fill('DifferentPassword123!')
    await registerSubmitButton(page).click()

    await expect(page.locator('.auth-error')).toHaveText(/password non coincidono/i, { timeout: 5000 })

    // Controllo lato client: nessuna richiesta di registrazione deve partire.
    expect(registerRequestSent).toBe(false)
  })
})