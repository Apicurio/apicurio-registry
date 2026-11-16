// Captures the screenshots in images/ from the running example. Run after `docker compose up -d` and
// ./demo.sh (the screenshots show the data it creates):
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
  await page.waitForTimeout(1000);
  await page.screenshot({ path: path.join(OUT, `${name}.png`) });
  console.log(`images/${name}.png`);
}

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

  await page.goto(`${UI}/rules`);
  await shot(page, '01-global-rules');
  await page.goto(`${UI}/explore/orders/order-placed/rules`);
  await shot(page, '02-artifact-rules');
  await page.goto(`${UI}/explore/sandbox/rules`);
  await shot(page, '03-group-rules');
  await page.goto(`${UI}/explore/orders/customer`);
  await shot(page, '04-customer-versions');
  await page.goto(`${UI}/drafts`);
  await shot(page, '05-drafts');
  await page.goto(`${UI}/explore/orders/shipment/versions/1/references`);
  await shot(page, '06-shipment-references');

  await browser.close();
})();
