#!/usr/bin/env bash
#
# Tests that a previous CLI release can upgrade to the current version.
#
# Downloads the previous release from Maven Central, installs it,
# sets up a mock repo serving the current SNAPSHOT as the "new" version,
# and runs the upgrade. Then runs smoke tests on the upgraded CLI.
#
# Skips gracefully if the previous release is not yet available on Maven Central.
#
# Prerequisites:
#   - Docker
#   - curl
#   - A built CLI ZIP in cli/target/ (run: mvn package -pl cli -am -DskipTests)
#
# Usage:
#   ./cli/src/test/scripts/test-upgrade-from-previous.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
CLI_TARGET="$PROJECT_ROOT/cli/target"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}PASS${NC}: $1"; }
fail() { echo -e "${RED}FAIL${NC}: $1"; FAILURES=$((FAILURES + 1)); }
info() { echo -e "${YELLOW}INFO${NC}: $1"; }
skip() { echo -e "${YELLOW}SKIP${NC}: $1"; exit 0; }

FAILURES=0
CONTAINER_NAME="acr-test-upgrade-$$"
WORK_DIR=""

cleanup() {
    if [ -n "$CONTAINER_NAME" ]; then
        docker rm -f "$CONTAINER_NAME" 2>/dev/null || true
    fi
    if [ -n "$WORK_DIR" ] && [ -d "$WORK_DIR" ]; then
        rm -rf "$WORK_DIR"
    fi
}
trap cleanup EXIT

# --- Detect platform ---

detect_platform() {
    local os arch
    case "$(uname -s)" in
        Linux*)  os="linux";;
        Darwin*) os="osx";;
        *)       echo "Unsupported OS: $(uname -s)"; exit 1;;
    esac
    case "$(uname -m)" in
        x86_64|amd64)   arch="x86_64";;
        aarch64|arm64)  arch="aarch_64";;
        *)              echo "Unsupported arch: $(uname -m)"; exit 1;;
    esac
    echo "${os}-${arch}"
}

PLATFORM="$(detect_platform)"
info "Detected platform: $PLATFORM"

# --- Determine versions ---

MAVEN_CENTRAL="https://repo1.maven.org/maven2/io/apicurio/apicurio-registry-cli"

CURRENT_VERSION=$(grep '<version>' "$PROJECT_ROOT/cli/pom.xml" | head -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
info "Current project version: $CURRENT_VERSION"

CURRENT_BASE="${CURRENT_VERSION%-SNAPSHOT}"

# Fetch all released versions from Maven Central and find the latest one
# that is strictly lower than the current version (ignoring SNAPSHOT suffix).
info "Fetching version list from Maven Central..."
METADATA_XML=$(curl -sf "$MAVEN_CENTRAL/maven-metadata.xml" 2>/dev/null) || skip "Could not fetch maven-metadata.xml from Maven Central"

# Extract versions, keep only community (no redhat/qualifier suffixes), sort, pick latest < current
PREVIOUS_VERSION=$(echo "$METADATA_XML" \
    | grep -oP '<version>\K[0-9]+\.[0-9]+\.[0-9]+(?=</version>)' \
    | sort -t. -k1,1n -k2,2n -k3,3n \
    | while IFS=. read -r maj min pat; do
        IFS=. read -r cmaj cmin cpat <<< "$CURRENT_BASE"
        if [ "$maj" -lt "$cmaj" ] || \
           { [ "$maj" -eq "$cmaj" ] && [ "$min" -lt "$cmin" ]; } || \
           { [ "$maj" -eq "$cmaj" ] && [ "$min" -eq "$cmin" ] && [ "$pat" -lt "$cpat" ]; }; then
            echo "${maj}.${min}.${pat}"
        fi
    done | tail -1)

if [ -z "$PREVIOUS_VERSION" ]; then
    skip "No previous release found on Maven Central that is older than $CURRENT_BASE"
fi
info "Previous release version to test: $PREVIOUS_VERSION"

# --- Find current CLI ZIP ---

CLI_ZIP=$(find "$CLI_TARGET" -maxdepth 1 -name "apicurio-registry-cli-*-${PLATFORM}.zip" | head -1)
if [ -z "$CLI_ZIP" ] || [ ! -f "$CLI_ZIP" ]; then
    echo "CLI ZIP not found in $CLI_TARGET. Build first: mvn package -pl cli -am -DskipTests"
    exit 1
fi
info "Current CLI ZIP: $CLI_ZIP"

# --- Download previous release ---

PREVIOUS_ZIP_NAME="apicurio-registry-cli-${PREVIOUS_VERSION}-${PLATFORM}.zip"
PREVIOUS_URL="${MAVEN_CENTRAL}/${PREVIOUS_VERSION}/${PREVIOUS_ZIP_NAME}"

WORK_DIR="$(mktemp -d)"
PREVIOUS_ZIP="$WORK_DIR/$PREVIOUS_ZIP_NAME"

info "Downloading previous release from $PREVIOUS_URL"

HTTP_CODE=$(curl -s -o "$PREVIOUS_ZIP" -w "%{http_code}" "$PREVIOUS_URL" || echo "000")
if [ "$HTTP_CODE" != "200" ]; then
    skip "Previous release $PREVIOUS_VERSION not available on Maven Central (HTTP $HTTP_CODE). Test will be relevant after $PREVIOUS_VERSION is released."
fi
info "Downloaded previous release: $PREVIOUS_ZIP"

# --- Set up directories ---

INSTALL_HOME="$WORK_DIR/install"
USER_HOME="$WORK_DIR/home"
REPO_DIR="$WORK_DIR/repo"

mkdir -p "$INSTALL_HOME" "$USER_HOME/bin" "$USER_HOME" "$REPO_DIR"

# --- Install previous version ---

PREV_UNZIP="$WORK_DIR/prev-unzip"
mkdir -p "$PREV_UNZIP"
unzip -q "$PREVIOUS_ZIP" -d "$PREV_UNZIP"

export HOME="$USER_HOME"
export ACR_INSTALL_PATH="$INSTALL_HOME"
export ACR_CURRENT_HOME="$PREV_UNZIP"

"$PREV_UNZIP/acr" install 2>/dev/null
pass "Previous version $PREVIOUS_VERSION installed"

export ACR_HOME="$INSTALL_HOME"
export ACR_CURRENT_HOME="$INSTALL_HOME"

# --- Prepare mock repo with current version as upgrade ---

UPGRADE_VERSION="${PREVIOUS_VERSION}.1"
ARTIFACT_DIR="$REPO_DIR/$UPGRADE_VERSION"
mkdir -p "$ARTIFACT_DIR"
cp "$CLI_ZIP" "$ARTIFACT_DIR/apicurio-registry-cli-${UPGRADE_VERSION}-${PLATFORM}.zip"

cat > "$REPO_DIR/maven-metadata.xml" <<XMLEOF
<?xml version="1.0" encoding="UTF-8"?>
<metadata>
  <groupId>io.apicurio</groupId>
  <artifactId>apicurio-registry-cli</artifactId>
  <versioning>
    <latest>$UPGRADE_VERSION</latest>
    <versions>
      <version>$PREVIOUS_VERSION</version>
      <version>$UPGRADE_VERSION</version>
    </versions>
  </versioning>
</metadata>
XMLEOF

# --- Start mock repo ---

docker run -d --name "$CONTAINER_NAME" \
    -v "$REPO_DIR:/usr/share/nginx/html:ro" \
    -p 0:80 \
    nginx:alpine >/dev/null
sleep 1
REPO_PORT=$(docker port "$CONTAINER_NAME" 80 | head -1 | cut -d: -f2)
REPO_URL="http://localhost:${REPO_PORT}"
info "Mock repo at $REPO_URL (serving $UPGRADE_VERSION)"

# Configure previous CLI to use mock repo
# The previous version may use old property names — try both
"$INSTALL_HOME/acr_runner" config set "internal.update.repo-url=$REPO_URL" 2>/dev/null \
    || "$INSTALL_HOME/acr_runner" config set "acr.update.repo.url=$REPO_URL" 2>/dev/null \
    || "$INSTALL_HOME/acr_runner" config set "update.repo.url=$REPO_URL" 2>/dev/null \
    || info "Could not set repo URL via config command — old CLI may not have config command"

# ============================================================
# TEST 1: Upgrade from previous release
# ============================================================

info "--- Test 1: Upgrade from $PREVIOUS_VERSION ---"

BEFORE_MTIME=$(stat -c %Y "$INSTALL_HOME/acr_runner" 2>/dev/null || stat -f %m "$INSTALL_HOME/acr_runner")
sleep 1

# Try update; the old CLI may not support the new flags, so fall back to --path
if VERSION="$PREVIOUS_VERSION" "$INSTALL_HOME/acr_runner" update "$UPGRADE_VERSION" 2>/dev/null; then
    pass "Upgrade command succeeded"
elif "$INSTALL_HOME/acr_runner" update --path "$ARTIFACT_DIR/apicurio-registry-cli-${UPGRADE_VERSION}-${PLATFORM}.zip" 2>/dev/null; then
    pass "Upgrade via --path succeeded (old CLI may not support auto-update)"
else
    fail "Upgrade failed"
fi

AFTER_MTIME=$(stat -c %Y "$INSTALL_HOME/acr_runner" 2>/dev/null || stat -f %m "$INSTALL_HOME/acr_runner")

if [ "$AFTER_MTIME" != "$BEFORE_MTIME" ]; then
    pass "Binary was updated"
else
    fail "Binary was NOT updated"
fi

# ============================================================
# TEST 2: Smoke test — upgraded CLI basic commands work
# ============================================================

info "--- Test 2: Smoke tests on upgraded CLI ---"

if "$INSTALL_HOME/acr_runner" version 2>/dev/null | grep -q "CLI version"; then
    pass "version command works"
else
    fail "version command failed"
fi

if "$INSTALL_HOME/acr_runner" config 2>/dev/null | grep -q "update.check-enabled\|update.repo.url\|Property"; then
    pass "config command works"
else
    fail "config command failed or not available"
fi

if "$INSTALL_HOME/acr_runner" --help 2>&1 | grep -q "Usage: acr"; then
    pass "help output works"
else
    fail "help output failed"
fi

# ============================================================
# Summary
# ============================================================

echo ""
if [ "$FAILURES" -eq 0 ]; then
    echo -e "${GREEN}All tests passed.${NC}"
else
    echo -e "${RED}${FAILURES} test(s) failed.${NC}"
    exit 1
fi
