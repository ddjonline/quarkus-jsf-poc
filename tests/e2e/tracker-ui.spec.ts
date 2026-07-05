import { test, expect } from '@playwright/test';

test.describe('FreightTrack tracker UI', () => {

  test('renders empty state by default', async ({ page }) => {
    await page.goto('/tracker.xhtml');
    await expect(page.getByTestId('empty-state')).toBeVisible();
    await expect(page.getByTestId('pro-input').first()).toBeVisible();
    await expect(page.locator('.ft-header')).toBeVisible();
    await expect(page.locator('.ft-footer')).toBeVisible();
  });

  test('counter updates on add/remove rows', async ({ page }) => {
    await page.goto('/tracker.xhtml');
    await expect(page.locator('.pro-counter')).toHaveText('1/10');

    await page.locator('.pro-add-link').click();
    await expect(page.locator('.pro-counter')).toHaveText('2/10');
    await expect(page.getByTestId('pro-input')).toHaveCount(2);

    await page.getByTestId('remove-pro').first().click();
    await expect(page.locator('.pro-counter')).toHaveText('1/10');
  });

  test('find button label changes with input count', async ({ page }) => {
    await page.goto('/tracker.xhtml');
    await page.getByTestId('pro-input').first().fill('4821763');
    await expect(page.getByTestId('find-shipments')).toHaveValue('Find My Shipment');

    await page.locator('.pro-add-link').click();
    await page.getByTestId('pro-input').nth(1).fill('3390045');
    await expect(page.getByTestId('find-shipments')).toHaveValue('Find My Shipments (2)');
  });

  test('no horizontal scroll at 375px', async ({ page }) => {
    await page.goto('/tracker.xhtml');
    const bodyWidth = await page.evaluate(() => document.body.scrollWidth);
    const viewportWidth = page.viewportSize()?.width ?? 375;
    expect(bodyWidth).toBeLessThanOrEqual(viewportWidth + 1);
  });
});
