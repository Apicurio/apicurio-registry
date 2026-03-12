# Running Playwright Tests with Docker

This guide explains how to run the Apicurio Registry UI Playwright tests using Docker.

## Why Docker?

The Playwright browser packages are optimized for Ubuntu and may not work correctly on other Linux distributions (e.g., Fedora). Running tests in the official Playwright Docker container ensures consistent results across all platforms.

## Prerequisites

- **Docker** installed and running
- **Registry UI** running on port 8888 (or custom port via `REGISTRY_UI_URL`)

## Quick Start

### 1. Start the UI

```bash
cd ui/ui-app
npm install
npm run dev
```

The UI should be accessible at http://localhost:8888

### 2. Run All Tests

```bash
cd ui/tests
./run-in-docker.sh
```

### 3. View Test Results

After tests complete, view the HTML report:

```bash
open playwright-report/index.html
# Or on Linux:
xdg-open playwright-report/index.html
```

## Advanced Usage

### Run Specific Test File

Run a single test spec file:

```bash
./run-in-docker.sh smoke.spec.ts
./run-in-docker.sh masthead.spec.ts
./run-in-docker.sh explore.spec.ts
```

### Run Tests Matching Pattern

Run tests that match a specific pattern:

```bash
./run-in-docker.sh --grep "masthead"
./run-in-docker.sh --grep "App - Has Title"
```

### Run with Custom UI URL

```bash
REGISTRY_UI_URL=http://localhost:9000 ./run-in-docker.sh
```

### Run with Specific Reporter

```bash
./run-in-docker.sh --reporter=list
```

### Run in Debug Mode

```bash
./run-in-docker.sh --debug
```

### Show Help

View all available options:

```bash
./run-in-docker.sh --help
```

## Available Test Files

- `smoke.spec.ts` - Basic smoke test
- `masthead.spec.ts` - Masthead logo verification
- `explore.spec.ts` - Explore page functionality (create group, artifacts, etc.)
- `globalRules.spec.ts` - Global rules configuration
- `referenceGraph.spec.ts` - Reference graph visualization
- `settings.spec.ts` - Settings page

## Troubleshooting

### UI Not Accessible

If you see `✗ UI is not accessible`, make sure:
1. The UI is running (`npm run dev` in `ui-app`)
2. The UI is accessible at http://localhost:8888
3. No firewall is blocking localhost connections

### Docker Network Issues

On some systems, `--network=host` may not work. Edit `run-in-docker.sh` and replace:
- `REGISTRY_UI_URL="${REGISTRY_UI_URL:-http://localhost:8888}"`
- with: `REGISTRY_UI_URL="${REGISTRY_UI_URL:-http://host.docker.internal:8888}"`

### Permission Denied

If you get "Permission denied", make the script executable:

```bash
chmod +x run-in-docker.sh
```

## Test Reports

After running tests, reports are saved in:

- **HTML Report**: `playwright-report/index.html` (interactive, visual)
- **JSON Results**: `playwright-report/results.json` (machine-readable)
- **Traces**: `test-results/` (for debugging failures)

## CI/CD Usage

In CI/CD pipelines, you can run tests with:

```bash
cd ui/tests
./run-in-docker.sh --reporter=json
```

The script exits with code 0 on success, non-zero on failure.

## Environment Variables

- `REGISTRY_UI_URL` - URL of the UI to test (default: http://localhost:8888)
- `IS_DOWNSTREAM` - Set to "true" for Red Hat downstream builds
- `EXPECTED_ALT` - Expected alt text for logo (auto-detected from IS_DOWNSTREAM)
- `EXPECTED_LOGO_SRC` - Expected logo source path (auto-detected from IS_DOWNSTREAM)

## Next Steps

After verifying tests run successfully with Docker, you can:

1. Fix any failing tests due to PatternFly v6 selector changes
2. Add new tests for new UI features
3. Integrate into CI/CD pipeline

## Resources

- [Playwright Documentation](https://playwright.dev)
- [Playwright Docker Images](https://playwright.dev/docs/docker)
- [Apicurio Registry Documentation](https://www.apicur.io/registry/)
