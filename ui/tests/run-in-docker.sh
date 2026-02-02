#!/bin/bash

# Script to run Playwright tests in Docker using the official Playwright image
# This is useful on systems where Playwright browser packages aren't well supported (e.g., Fedora)
#
# Usage:
#   ./run-in-docker.sh                    # Run all tests
#   ./run-in-docker.sh smoke.spec.ts      # Run specific test file
#   ./run-in-docker.sh --grep "App"       # Run tests matching pattern
#   ./run-in-docker.sh --help             # Show this help

set -e

# Show usage and exit
show_help() {
    cat << EOF
Playwright Tests - Docker Runner

Usage: ./run-in-docker.sh [PLAYWRIGHT_OPTIONS]

Examples:
  ./run-in-docker.sh                          Run all tests
  ./run-in-docker.sh smoke.spec.ts            Run specific test file
  ./run-in-docker.sh masthead.spec.ts         Run masthead tests
  ./run-in-docker.sh --grep "App"             Run tests matching "App"
  ./run-in-docker.sh --reporter=list          Use list reporter
  ./run-in-docker.sh --debug                  Run in debug mode

Environment Variables:
  REGISTRY_UI_URL    URL of the UI to test (default: http://localhost:8888)

For more options, see: https://playwright.dev/docs/test-cli
EOF
    exit 0
}

# Check for help flag
if [[ "$*" == *"--help"* ]] || [[ "$*" == *"-h"* ]]; then
    show_help
fi

# Configuration
PLAYWRIGHT_VERSION="v1.58.0-jammy"
PLAYWRIGHT_IMAGE="mcr.microsoft.com/playwright:${PLAYWRIGHT_VERSION}"
REGISTRY_UI_URL="${REGISTRY_UI_URL:-http://localhost:8888}"
TESTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Playwright Tests - Docker Runner${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${YELLOW}Configuration:${NC}"
echo -e "  Tests directory: ${TESTS_DIR}"
echo -e "  Registry UI URL: ${REGISTRY_UI_URL}"
echo -e "  Playwright image: ${PLAYWRIGHT_IMAGE}"

# Show what tests will run
if [ -z "$*" ]; then
    echo -e "  Tests to run: ${GREEN}All tests${NC}"
else
    echo -e "  Tests to run: ${GREEN}$*${NC}"
fi
echo ""

# Check if UI is accessible
echo -e "${YELLOW}Checking if UI is accessible at ${REGISTRY_UI_URL}...${NC}"
if command -v curl &> /dev/null; then
    if curl -s -f -o /dev/null "${REGISTRY_UI_URL}"; then
        echo -e "${GREEN}✓ UI is accessible${NC}"
    else
        echo -e "${RED}✗ UI is not accessible at ${REGISTRY_UI_URL}${NC}"
        echo -e "${RED}  Make sure the UI is running (npm run dev in ui-app)${NC}"
        exit 1
    fi
else
    echo -e "${YELLOW}⚠ curl not found, skipping UI accessibility check${NC}"
fi

echo ""
echo -e "${YELLOW}Pulling Playwright Docker image...${NC}"
docker pull "${PLAYWRIGHT_IMAGE}"

echo ""
echo -e "${YELLOW}Running Playwright tests...${NC}"
echo ""

# Run tests in Docker container
docker run --rm \
  --network=host \
  -v "${TESTS_DIR}:/tests" \
  -w /tests \
  -e REGISTRY_UI_URL="${REGISTRY_UI_URL}" \
  -e CI=true \
  "${PLAYWRIGHT_IMAGE}" \
  sh -c "npm ci && npx playwright test $*"

# Capture exit code
EXIT_CODE=$?

echo ""
if [ $EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}✓ Tests completed successfully${NC}"
    echo -e "${GREEN}========================================${NC}"
else
    echo -e "${RED}========================================${NC}"
    echo -e "${RED}✗ Tests failed with exit code ${EXIT_CODE}${NC}"
    echo -e "${RED}========================================${NC}"
fi

echo ""
echo -e "${YELLOW}Test results location:${NC}"
echo -e "  HTML report: ${TESTS_DIR}/playwright-report/index.html"
echo -e "  Test results: ${TESTS_DIR}/test-results/"
echo ""

exit $EXIT_CODE
