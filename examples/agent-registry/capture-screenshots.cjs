// Captures the screenshots in images/ from the running demo. Run after `docker compose up -d` and
// ./demo.sh (the screenshots show the data it creates):
//
//   npx -y -p playwright@1 node capture-screenshots.cjs
//
// Not needed to run the demo; it only refreshes the guide's images.
const { chromium } = require('playwright');
const path = require('path');

const OPEN_UI = process.env.OPEN_UI || 'http://localhost:8889';
const SECURED_UI = process.env.SECURED_UI || 'http://localhost:8888';
const DASHBOARD = process.env.DASHBOARD || 'http://localhost:11020';
const OUT = path.join(__dirname, 'images');

async function shot(page, name) {
  await page.waitForLoadState('networkidle').catch(() => {});
  await page.waitForTimeout(800);
  await page.screenshot({ path: path.join(OUT, `${name}.png`) });
  console.log(`images/${name}.png`);
}

async function login(page, user, password) {
  await page.goto(`${SECURED_UI}/agents`);
  await page.waitForSelector('#username', { timeout: 30000 });
  await page.fill('#username', user);
  await page.fill('#password', password);
  await page.click('#kc-login');
  await page.waitForURL(`${SECURED_UI}/**`, { timeout: 30000 });
}

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

  // Part 1 and 2: the artifacts and their references
  await page.goto(`${OPEN_UI}/explore/demo`);
  await shot(page, '01-demo-group');
  await page.goto(`${OPEN_UI}/explore/demo/trip-planner/versions/1/documentation`);
  await shot(page, '02-agent-card');
  await page.goto(`${OPEN_UI}/explore/demo/trip-planner/versions/1/references`);
  await shot(page, '03-agent-card-references');

  // Part 3: the MCP tool view and its compatibility rule
  await page.goto(`${OPEN_UI}/explore/demo/search_guides/versions/2/documentation`);
  await shot(page, '04-mcp-tool');
  await page.goto(`${OPEN_UI}/explore/demo/search_guides/rules`);
  await shot(page, '05-mcp-tool-rules');

  // Part 4: rendering a prompt template in the console
  await page.goto(`${OPEN_UI}/explore/demo/city-briefing/versions/1/documentation`);
  await page.waitForSelector('[aria-label="city"]', { timeout: 30000 });
  await page.fill('[aria-label="city"]', 'Lisbon');
  await page.fill('[aria-label="weather"]', 'sunny, 24 C');
  await page.getByRole('button', { name: /render/i }).first().click();
  const rendered = page.getByText('Write a short briefing for a visit to Lisbon').first();
  await rendered.waitFor({ timeout: 30000 });
  // The console scrolls an inner panel, so make the window tall enough to show the result
  await page.setViewportSize({ width: 1440, height: 1700 });
  await rendered.scrollIntoViewIfNeeded();
  await shot(page, '06-prompt-template-render');
  await page.setViewportSize({ width: 1440, height: 900 });

  // Part 6: the Agents tab
  await page.goto(`${OPEN_UI}/agents`);
  await shot(page, '07-agents-tab');

  // Part 8: importing tools from the live MCP server, previewed in the console
  await page.goto(`${OPEN_UI}/explore/weather-tools/weather`);
  await page.click('[data-testid="header-btn-import-mcp-tools"]');
  await page.fill('[data-testid="form-mcp-url"]', 'http://weather-server:11040/mcp');
  await page.click('[data-testid="modal-btn-preview"]');
  await page.waitForSelector('[data-testid="mcp-import-result"]', { timeout: 30000 });
  await shot(page, '08-mcp-import-preview');
  await page.keyboard.press('Escape');

  // Part 9: the Quarkus LangChain4j briefing (calls Ollama)
  if (!process.env.SKIP_LLM) {
    await page.goto(DASHBOARD);
    await page.getByRole('button', { name: /briefing/i }).click();
    await page.waitForSelector('text=A2A translate', { timeout: 240000 });
    await page.waitForTimeout(500);
    await page.screenshot({ path: path.join(OUT, '09-briefing.png'), fullPage: true });
    console.log('images/09-briefing.png');
  }

  // Part 10: the same Agents tab, for two users of the secured Registry
  for (const [user, password, name] of [['user', 'user', '10-agents-as-user'], ['developer', 'developer', '11-agents-as-developer']]) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const p = await context.newPage();
    await login(p, user, password);
    await p.goto(`${SECURED_UI}/agents`);
    await shot(p, name);
    await context.close();
  }

  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
