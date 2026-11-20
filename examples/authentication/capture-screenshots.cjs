// Captures the screenshots in images/ from the running example. Run after `docker compose up -d` and
// ./demo.sh:
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

async function login(browser) {
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  await page.goto(`${UI}/explore`);
  await page.waitForSelector('#username', { timeout: 30000 });
  return page;
}

(async () => {
  const browser = await chromium.launch();
  let page = await login(browser);
  await shot(page, '01-keycloak-login');
  await page.fill('#username', 'developer');
  await page.fill('#password', 'developer');
  await page.click('#kc-login');
  await page.waitForURL(`${UI}/**`, { timeout: 30000 });
  await page.goto(`${UI}/rules`);
  await shot(page, '03-developer-global-rules');
  await page.close();

  page = await login(browser);
  await page.fill('#username', 'admin');
  await page.fill('#password', 'admin');
  await page.click('#kc-login');
  await page.waitForURL(`${UI}/**`, { timeout: 30000 });
  await page.goto(`${UI}/rules`);
  await shot(page, '04-admin-global-rules');
  await browser.close();
})();
