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
  await page.waitForTimeout(1500);
  await page.screenshot({ path: path.join(OUT, `${name}.png`) });
  console.log(`images/${name}.png`);
}

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  await page.goto(`${UI}/explore/orders-manual/Order/versions/1/references`);
  await shot(page, '01-order-references');
  await page.getByRole('button', { name: 'Graph' }).click();
  await shot(page, '02-order-reference-graph');
  await page.goto(`${UI}/explore/common-manual/Address/versions/1/references`);
  await page.getByText('View artifacts that reference this artifact').click();
  await shot(page, '03-address-inbound');
  await page.goto(`${UI}/explore/events/customer-events/versions/1/references`);
  await page.getByRole('button', { name: 'Graph' }).click();
  await shot(page, '04-asyncapi-reference-graph');
  await browser.close();
})();
