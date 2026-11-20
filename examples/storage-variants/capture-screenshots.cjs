// Captures the screenshots in images/ from a running GitOps registry. Run after
// `VARIANTS=gitops KEEP=1 ./demo.sh` (KEEP leaves the variant running):
//
//   npx -y -p playwright@1 node capture-screenshots.cjs
//
// Not needed to run the example; it only refreshes the guide's images.
const { chromium } = require('playwright');
const path = require('path');

const UI = process.env.UI || 'http://localhost:8888';
const OUT = path.join(__dirname, 'images');

async function shot(page, name) {
  await page.waitForLoadState('networkidle').catch(() => {});
  await page.waitForTimeout(1500);
  await page.screenshot({ path: path.join(OUT, `${name}.png`) });
  console.log(`images/${name}.png`);
}

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  await page.goto(`${UI}/explore/orders/order-placed`);
  await shot(page, '01-gitops-read-only');
  await browser.close();
})();
