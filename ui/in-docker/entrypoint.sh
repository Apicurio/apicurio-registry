#!/bin/bash

# This script runs startup commands before dropping into bash
echo "Starting container initialization..."

# Set working directory
cd /mnt/apicurio-registry-ui

# Display current directory and user info
echo "Working directory: $(pwd)"
echo "Running as user: $(whoami) ($(id -u):$(id -g))"

# Check if tests directory exists and install dependencies if needed
if [ -d "tests" ]; then
    echo "Found tests directory, checking dependencies..."
    cd tests
    npm install
fi

# Install Playwright
npx playwright install

# Set working directory
cd /mnt/apicurio-registry-ui

echo ""
echo "=== Container Ready ==="
echo "Available commands:"
echo "  - cd tests && npm run test                    # Run all tests"
echo ""
echo "Environment variables:"
echo "  - REGISTRY_UI_URL: ${REGISTRY_UI_URL:-http://localhost:8888}"
echo ""

# Execute the command passed to the container (typically bash)
exec "$@"
