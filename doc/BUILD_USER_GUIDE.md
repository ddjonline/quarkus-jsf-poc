# Build User Guide

Steps and commands to regenerate the screenshots and user guide.

## Prerequisites

- Node.js (for Playwright)
- Docker environment running (`docker compose up -d`)
- `npm install` has been run

## Screenshots

The screenshot script uses Playwright to automate browser interactions and
capture full-page screenshots at 4 states.

### Install Playwright browsers (one-time)

```bash
npx playwright install chromium
```

### Run the script

```bash
node scripts/take-screenshots.mjs
```

Output:

```
doc/screenshots/01-single-pro.png    # Desktop: 1 PRO with IN_TRANSIT result
doc/screenshots/02-two-pros.png      # Desktop: 2 PROs (IN_TRANSIT + DELIVERED)
doc/screenshots/03-three-pros.png    # Desktop: 3 PROs (includes NOT_FOUND)
doc/screenshots/04-mobile.png        # Mobile: 375x812 viewport
```

### What the script does

| Step | Action |
|------|--------|
| 1 PRO | Types `4821763`, blur (auto-checks), clicks Find, captures |
| 2 PROs | Clicks "+ Add Number", types `3390045` in row 2, blur, Find, captures |
| 3 PROs | Clicks "+ Add Number", types `12345` in row 3, blur, Find, captures |
| Mobile | New 375×812 viewport, types `4821763`, blur, Find, captures |

Viewport for desktop shots: 1280×900.

### Useful Playwright commands

```bash
# Run E2E tests
npx playwright test

# Run with UI debugger
npx playwright test --ui

# View traces from failed tests
npx playwright show-trace test-results/<trace>.zip

# Take a single manual screenshot from CLI
npx playwright screenshot --viewport-size=1280,900 \
  http://localhost:8080/tracker.xhtml manual-shot.png
```

## Cleanup

```bash
# Remove generated screenshots
rm -f doc/screenshots/*.png

# Remove Playwright browsers and node_modules
npx playwright uninstall --all
rm -rf node_modules

# Shut down Docker environment
docker compose down
```