import { chromium } from 'playwright';

const BASE = 'http://localhost:8080';
const SCREENSHOTS = 'doc/screenshots';

async function main() {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await ctx.newPage();

  await page.goto(`${BASE}/tracker.xhtml`);
  await page.waitForLoadState('networkidle');

  // --- 1 PRO number ---
  const input1 = page.locator('[data-testid="pro-input"]').first();
  await input1.fill('4821763');
  await input1.blur();           // validateEntry auto-checks
  await page.waitForTimeout(500);

  await page.locator('[data-testid="find-shipments"]').click();
  await page.waitForTimeout(2000);

  await page.screenshot({ path: `${SCREENSHOTS}/01-single-pro.png`, fullPage: true });
  console.log('✓ 01-single-pro.png');

  // --- 2 PRO numbers ---
  await page.locator('text=+ Add Number').click();
  await page.waitForTimeout(500);

  const inputs2 = page.locator('[data-testid="pro-input"]');
  await inputs2.nth(1).fill('3390045');
  await inputs2.nth(1).blur();
  await page.waitForTimeout(500);

  await page.locator('[data-testid="find-shipments"]').click();
  await page.waitForTimeout(2000);

  await page.screenshot({ path: `${SCREENSHOTS}/02-two-pros.png`, fullPage: true });
  console.log('✓ 02-two-pros.png');

  // --- 3 PRO numbers ---
  await page.locator('text=+ Add Number').click();
  await page.waitForTimeout(500);

  const inputs3 = page.locator('[data-testid="pro-input"]');
  await inputs3.nth(2).fill('12345');
  await inputs3.nth(2).blur();
  await page.waitForTimeout(500);

  await page.locator('[data-testid="find-shipments"]').click();
  await page.waitForTimeout(2000);

  await page.screenshot({ path: `${SCREENSHOTS}/03-three-pros.png`, fullPage: true });
  console.log('✓ 03-three-pros.png');

  // --- Mobile screenshot ---
  const mobileCtx = await browser.newContext({ viewport: { width: 375, height: 812 } });
  const mobilePage = await mobileCtx.newPage();

  await mobilePage.goto(`${BASE}/tracker.xhtml`);
  await mobilePage.waitForLoadState('networkidle');

  const mInput = mobilePage.locator('[data-testid="pro-input"]').first();
  await mInput.fill('4821763');
  await mInput.blur();
  await mobilePage.waitForTimeout(500);

  await mobilePage.locator('[data-testid="find-shipments"]').click();
  await mobilePage.waitForTimeout(2000);

  await mobilePage.screenshot({ path: `${SCREENSHOTS}/04-mobile.png`, fullPage: true });
  console.log('✓ 04-mobile.png');

  await browser.close();
  console.log('\nAll screenshots captured.');
}

main().catch(err => { console.error(err); process.exit(1); });