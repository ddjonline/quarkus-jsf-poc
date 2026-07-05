import { test, expect } from '@playwright/test';

test.describe('FreightTrack shipment search', () => {

  test('searching 4821763 and 3390045 renders 2 cards with correct badges', async ({ page }) => {
    await page.goto('/tracker.xhtml');

    await page.getByTestId('pro-input').first().fill('4821763');
    await page.locator('.pro-add-link').click();
    await page.getByTestId('pro-input').nth(1).fill('3390045');

    await page.getByTestId('find-shipments').click();
    await page.waitForSelector('[data-testid="result-card"]');

    const cards = page.getByTestId('result-card');
    await expect(cards).toHaveCount(2);
    await expect(page.getByTestId('results-count')).toHaveText('2');

    await expect(page.locator('.badge-transit').first()).toBeVisible();
    await expect(page.locator('.badge-delivered').first()).toBeVisible();
  });

  test('expanded card shows detail and timeline', async ({ page }) => {
    await page.goto('/tracker.xhtml');
    await page.getByTestId('pro-input').first().fill('4821763');
    await page.getByTestId('find-shipments').click();
    await page.waitForSelector('[data-testid="shipment-detail"]');

    await expect(page.getByTestId('shipment-detail')).toBeVisible();
    await expect(page.locator('.section-label').first()).toBeVisible();
    await expect(page.locator('.timeline-item')).toHaveCount(3);
  });
});
