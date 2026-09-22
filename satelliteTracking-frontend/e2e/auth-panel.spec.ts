import { test, expect } from '@playwright/test'

test.describe('Auth Panel', () => {
  test('should display auth panel on initial load', async ({ page }) => {
    await page.goto('/')

    await expect(page.locator('text=Satellite Tracker')).toBeVisible()
    await expect(page.locator('button:has-text("Accesso")')).toBeVisible()
    await expect(page.locator('button:has-text("Iscrizione")')).toBeVisible()
  })

  test('should switch between login and register tabs', async ({ page }) => {
    await page.goto('/')

    await expect(page.locator('text=Username o email')).toBeVisible()
    await page.click('button:has-text("Iscrizione")')
    await expect(page.locator('text=Username')).toBeVisible()
    await expect(page.locator('text=Email')).toBeVisible()
    await page.click('button:has-text("Accesso")')
    await expect(page.locator('text=Username o email')).toBeVisible()
  })
})
