import { test, expect } from '@playwright/test'
import { loginUsernameOrEmail, passwordField } from './helpers/auth-helpers'

test.describe('Auth Panel', () => {
  test('should display auth panel on initial load', async ({ page }) => {
    await page.goto('/')

    await expect(page.getByRole('heading', { name: 'Satellite Tracker' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Accesso', exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Iscrizione', exact: true })).toBeVisible()
  })

  test('should switch between login and register tabs', async ({ page }) => {
    await page.goto('/')

    // Login: un solo campo identificativo e nessuna conferma password
    await expect(loginUsernameOrEmail(page)).toBeVisible()
    await expect(page.getByLabel('Conferma password')).toHaveCount(0)

    await page.getByRole('button', { name: 'Iscrizione', exact: true }).click()

    // Registrazione: username, email, password e conferma; sparisce il campo di login
    await expect(page.getByLabel('Username', { exact: true })).toBeVisible()
    await expect(page.getByLabel('Email', { exact: true })).toBeVisible()
    await expect(passwordField(page)).toBeVisible()
    await expect(page.getByLabel('Conferma password')).toBeVisible()
    await expect(loginUsernameOrEmail(page)).toHaveCount(0)

    await page.getByRole('button', { name: 'Accesso', exact: true }).click()

    await expect(loginUsernameOrEmail(page)).toBeVisible()
    await expect(page.getByLabel('Conferma password')).toHaveCount(0)
  })
})