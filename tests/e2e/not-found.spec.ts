import { test, expect } from '@playwright/test';

test.describe('FreightTrack not-found handling', () => {

  test('unknown PRO renders not-found card', async ({ page }) => {
    await page.goto('/tracker.xhtml');
    await page.getByTestId('pro-input').first().fill('99999999999');
    await page.getByTestId('find-shipments').click();
    await page.waitForSelector('[data-testid="result-card"]');

    await expect(page.locator('.result-notfound')).toBeVisible();
  });

  test('unknown PRO does not drop valid results', async ({ page }) => {
    await page.goto('/tracker.xhtml');
    await page.getByTestId('pro-input').first().fill('4821763');
    await page.locator('.pro-add-link').click();
    await page.getByTestId('pro-input').nth(1).fill('99999999999');
    await page.getByTestId('find-shipments').click();
    await page.waitForSelector('[data-testid="result-card"]');

    await expect(page.getByTestId('result-card')).toHaveCount(2);
    await expect(page.locator('.badge-transit').first()).toBeVisible();
    await expect(page.locator('.result-notfound')).toBeVisible();
  });
});
