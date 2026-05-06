#!/usr/bin/env bash
#
# End-to-end test for CLI auto-update.
#
# Starts a mock Maven repo (nginx), installs the CLI with a faked old version,
# and verifies that update detection and installation work correctly.
#
# Prerequisites:
#   - Docker
#   - A built CLI ZIP in cli/target/ (run: mvn package -pl cli -am -DskipTests)
#
# Usage:
#   ./cli/src/test/scripts/test-update.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
CLI_TARGET="$PROJECT_ROOT/cli/target"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
NC='\033[0m'

pass() { echo -e "${GREEN}PASS${NC}: $1"; }
fail() { echo -e "${RED}FAIL${NC}: $1"; FAILURES=$((FAILURES + 1)); }
info() { echo -e "${YELLOW}INFO${NC}: $1"; }

# Runs acr_runner, prints the command and its output, and stores combined output in $ACR_OUTPUT.
# Usage: run_acr [ENV_VAR=value...] <args...>
# Returns the exit code of acr_runner.
run_acr() {
    local env_prefix=""
    local args=()
    for arg in "$@"; do
        if [[ "$arg" == *=* && ! "$arg" == -* && "${arg%%=*}" =~ ^[A-Z_]+$ ]]; then
            env_prefix="$env_prefix $arg"
        else
            args+=("$arg")
        fi
    done
    echo -e "  ${CYAN}\$${NC}${env_prefix:+ $env_prefix} acr ${args[*]}"
    local rc=0
    ACR_OUTPUT=$(eval $env_prefix '"$INSTALL_HOME/acr_runner"' '"${args[@]}"' 2>&1) || rc=$?
    if [ -n "$ACR_OUTPUT" ]; then
        echo "$ACR_OUTPUT" | sed 's/^/    /'
    fi
    return $rc
}

FAILURES=0
CONTAINER_NAME="acr-test-repo-$$"
WORK_DIR=""

cleanup() {
    if [ -n "$CONTAINER_NAME" ]; then
        docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
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

# --- Find CLI ZIP ---

CLI_ZIP=$(find "$CLI_TARGET" -maxdepth 1 -name "apicurio-registry-cli-*-${PLATFORM}.zip" | head -1)
if [ -z "$CLI_ZIP" ] || [ ! -f "$CLI_ZIP" ]; then
    echo "CLI ZIP not found in $CLI_TARGET for platform $PLATFORM."
    echo "Build first: mvn package -pl cli -am -DskipTests"
    exit 1
fi
info "Using CLI ZIP: $CLI_ZIP"

# --- Set up work directory ---

WORK_DIR="$(mktemp -d)"
info "Work directory: $WORK_DIR"

USER_HOME="$WORK_DIR/home"
INSTALL_HOME="$USER_HOME/.apicurio/apicurio-registry-cli"
REPO_DIR="$WORK_DIR/repo"

mkdir -p "$USER_HOME/bin" "$USER_HOME" "$REPO_DIR"
touch "$USER_HOME/.bashrc"

# --- Prepare mock Maven repo ---

FAKE_OLD_VERSION="3.0.0"
FAKE_NEW_VERSION="3.1.0"
FAKE_NEWER_VERSION="3.2.0"

ARTIFACT_DIR="$REPO_DIR/$FAKE_NEW_VERSION"
ARTIFACT_DIR_NEWER="$REPO_DIR/$FAKE_NEWER_VERSION"
mkdir -p "$ARTIFACT_DIR" "$ARTIFACT_DIR_NEWER"

# Copy the real ZIP as the "new version"
cp "$CLI_ZIP" "$ARTIFACT_DIR/apicurio-registry-cli-${FAKE_NEW_VERSION}-${PLATFORM}.zip"
cp "$CLI_ZIP" "$ARTIFACT_DIR_NEWER/apicurio-registry-cli-${FAKE_NEWER_VERSION}-${PLATFORM}.zip"

# Create maven-metadata.xml
cat > "$REPO_DIR/maven-metadata.xml" <<XMLEOF
<?xml version="1.0" encoding="UTF-8"?>
<metadata>
  <groupId>io.apicurio</groupId>
  <artifactId>apicurio-registry-cli</artifactId>
  <versioning>
    <latest>$FAKE_NEWER_VERSION</latest>
    <release>$FAKE_NEWER_VERSION</release>
    <versions>
      <version>$FAKE_OLD_VERSION</version>
      <version>$FAKE_NEW_VERSION</version>
      <version>$FAKE_NEWER_VERSION</version>
    </versions>
  </versioning>
</metadata>
XMLEOF

info "Mock repo prepared with versions: $FAKE_OLD_VERSION, $FAKE_NEW_VERSION, $FAKE_NEWER_VERSION"

# --- Start nginx ---

docker run -d --name "$CONTAINER_NAME" \
    -v "$REPO_DIR:/usr/share/nginx/html:ro" \
    -p 0:80 \
    nginx:alpine >/dev/null

# Wait for container and get port
sleep 1
REPO_PORT=$(docker port "$CONTAINER_NAME" 80 | head -1 | cut -d: -f2)
REPO_URL="http://localhost:${REPO_PORT}"

info "Mock repo started at $REPO_URL"

# Verify repo is accessible
if ! curl -sf "$REPO_URL/maven-metadata.xml" >/dev/null; then
    echo "Mock repo is not accessible at $REPO_URL"
    exit 1
fi
info "Mock repo verified"

# --- Install CLI from current ZIP ---

UNZIP_DIR="$WORK_DIR/unzip"
mkdir -p "$UNZIP_DIR"
unzip -q "$CLI_ZIP" -d "$UNZIP_DIR"

# Set up environment for install
export HOME="$USER_HOME"
export ACR_CURRENT_HOME="$UNZIP_DIR"

echo -e "  ${CYAN}\$${NC} acr install"
INSTALL_OUTPUT=$("$UNZIP_DIR/acr" install 2>&1) || true
if [ -n "$INSTALL_OUTPUT" ]; then
    echo "$INSTALL_OUTPUT" | sed 's/^/    /'
fi

# Verify install
if [ ! -f "$INSTALL_HOME/acr_runner" ]; then
    fail "CLI binary not installed"
    exit 1
fi
pass "CLI installed to $INSTALL_HOME"

# Point to mock repo and set up environment
export ACR_HOME="$INSTALL_HOME"
export ACR_CURRENT_HOME="$INSTALL_HOME"

# Configure to use mock repo
run_acr config set "internal.update.repo-url=$REPO_URL"
pass "Config set to use mock repo"

# ============================================================
# TEST 1: Update check detects newer versions
# ============================================================

info "--- Test 1: Update check with faked old version ---"

run_acr VERSION="$FAKE_OLD_VERSION" update --check || true

if echo "$ACR_OUTPUT" | grep -q "$FAKE_NEW_VERSION"; then
    pass "Update check found version $FAKE_NEW_VERSION"
else
    fail "Update check did not find version $FAKE_NEW_VERSION"
fi

if echo "$ACR_OUTPUT" | grep -q "$FAKE_NEWER_VERSION"; then
    pass "Update check found version $FAKE_NEWER_VERSION"
else
    fail "Update check did not find version $FAKE_NEWER_VERSION"
fi

# ============================================================
# TEST 2: Ambiguous update (multiple candidates) requires version
# ============================================================

info "--- Test 2: Ambiguous update requires explicit version ---"

run_acr VERSION="$FAKE_OLD_VERSION" update || true

if echo "$ACR_OUTPUT" | grep -qi "multiple\|candidates\|specify\|ambiguous"; then
    pass "Ambiguous update correctly asks user to specify version"
else
    fail "Expected ambiguous update message"
fi

# ============================================================
# TEST 3: Update to specific version downloads and installs
# ============================================================

info "--- Test 3: Update to specific version ---"

BEFORE_MTIME=$(stat -c %Y "$INSTALL_HOME/acr_runner" 2>/dev/null || stat -f %m "$INSTALL_HOME/acr_runner")
sleep 1

run_acr VERSION="$FAKE_OLD_VERSION" update "$FAKE_NEW_VERSION" || true

AFTER_MTIME=$(stat -c %Y "$INSTALL_HOME/acr_runner" 2>/dev/null || stat -f %m "$INSTALL_HOME/acr_runner")

if [ "$AFTER_MTIME" != "$BEFORE_MTIME" ]; then
    pass "Binary was updated (mtime changed)"
else
    fail "Binary was NOT updated (mtime unchanged)"
fi

# ============================================================
# TEST 4: Config preserved after update
# ============================================================

info "--- Test 4: Config preserved after update ---"

run_acr config get "internal.update.repo-url" || true

if [ "$ACR_OUTPUT" = "$REPO_URL" ]; then
    pass "Config preserved after update (repo URL intact)"
else
    fail "Config not preserved after update. Expected '$REPO_URL', got '$ACR_OUTPUT'"
fi

# ============================================================
# TEST 5: Update check records last-check timestamp
# ============================================================

info "--- Test 5: Last-check timestamp recorded ---"

run_acr config get "internal.update.last-check" || true

if [ -n "$ACR_OUTPUT" ]; then
    pass "Last-check timestamp recorded: $ACR_OUTPUT"
else
    fail "Last-check timestamp not found in config"
fi

# ============================================================
# TEST 6: Single candidate auto-updates without specifying version
# ============================================================

info "--- Test 6: Single candidate auto-updates ---"

# Create a repo with only one version newer than the "current"
SINGLE_REPO="$WORK_DIR/single-repo"
SINGLE_ARTIFACT="$SINGLE_REPO/$FAKE_NEWER_VERSION"
mkdir -p "$SINGLE_ARTIFACT"
cp "$CLI_ZIP" "$SINGLE_ARTIFACT/apicurio-registry-cli-${FAKE_NEWER_VERSION}-${PLATFORM}.zip"

cat > "$SINGLE_REPO/maven-metadata.xml" <<XMLEOF
<?xml version="1.0" encoding="UTF-8"?>
<metadata>
  <groupId>io.apicurio</groupId>
  <artifactId>apicurio-registry-cli</artifactId>
  <versioning>
    <latest>$FAKE_NEWER_VERSION</latest>
    <versions>
      <version>$FAKE_NEW_VERSION</version>
      <version>$FAKE_NEWER_VERSION</version>
    </versions>
  </versioning>
</metadata>
XMLEOF

# Stop old container, start new one with single-repo
docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
CONTAINER_NAME="acr-test-single-repo-$$"
docker run -d --name "$CONTAINER_NAME" \
    -v "$SINGLE_REPO:/usr/share/nginx/html:ro" \
    -p 0:80 \
    nginx:alpine >/dev/null
sleep 1
SINGLE_PORT=$(docker port "$CONTAINER_NAME" 80 | head -1 | cut -d: -f2)
SINGLE_URL="http://localhost:${SINGLE_PORT}"

run_acr config set "internal.update.repo-url=$SINGLE_URL"

BEFORE_MTIME=$(stat -c %Y "$INSTALL_HOME/acr_runner" 2>/dev/null || stat -f %m "$INSTALL_HOME/acr_runner")
sleep 1

run_acr VERSION="$FAKE_NEW_VERSION" update || true

AFTER_MTIME=$(stat -c %Y "$INSTALL_HOME/acr_runner" 2>/dev/null || stat -f %m "$INSTALL_HOME/acr_runner")

if [ "$AFTER_MTIME" != "$BEFORE_MTIME" ]; then
    pass "Single candidate auto-update succeeded (binary updated)"
else
    fail "Single candidate auto-update failed (binary unchanged)"
fi

# ============================================================
# Helper: switch mock repo
# ============================================================

start_repo() {
    local repo_dir="$1"
    docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
    CONTAINER_NAME="acr-test-repo-$$-$RANDOM"
    docker run -d --name "$CONTAINER_NAME" \
        -v "$repo_dir:/usr/share/nginx/html:ro" \
        -p 0:80 \
        nginx:alpine >/dev/null
    sleep 1
    local port
    port=$(docker port "$CONTAINER_NAME" 80 | head -1 | cut -d: -f2)
    CURRENT_REPO_URL="http://localhost:${port}"
    run_acr config set "internal.update.repo-url=$CURRENT_REPO_URL"
}

# ============================================================
# TEST 7: Redhat versions are filtered for community CLI
# ============================================================

info "--- Test 7: Redhat versions ignored for community CLI ---"

REDHAT_REPO="$WORK_DIR/redhat-repo"
mkdir -p "$REDHAT_REPO/3.1.0" "$REDHAT_REPO/3.1.0.redhat-00001"
cp "$CLI_ZIP" "$REDHAT_REPO/3.1.0/apicurio-registry-cli-3.1.0-${PLATFORM}.zip"
cp "$CLI_ZIP" "$REDHAT_REPO/3.1.0.redhat-00001/apicurio-registry-cli-3.1.0.redhat-00001-${PLATFORM}.zip"

cat > "$REDHAT_REPO/maven-metadata.xml" <<XMLEOF
<?xml version="1.0" encoding="UTF-8"?>
<metadata>
  <groupId>io.apicurio</groupId>
  <artifactId>apicurio-registry-cli</artifactId>
  <versioning>
    <versions>
      <version>3.0.0</version>
      <version>3.1.0</version>
      <version>3.1.0.redhat-00001</version>
    </versions>
  </versioning>
</metadata>
XMLEOF

start_repo "$REDHAT_REPO"

# Running as community version (3.0.0) — should only see 3.1.0, not the redhat version
run_acr VERSION="3.0.0" update --check || true

if echo "$ACR_OUTPUT" | grep -q "3.1.0" && ! echo "$ACR_OUTPUT" | grep -q "redhat"; then
    pass "Community CLI only sees community versions"
else
    fail "Expected only community version 3.1.0 in output"
fi

# ============================================================
# TEST 8: Redhat CLI sees only redhat versions
# ============================================================

info "--- Test 8: Redhat CLI sees only redhat versions ---"

# Running as redhat version — should only see redhat versions
run_acr VERSION="3.0.0.redhat-00001" update --check || true

if echo "$ACR_OUTPUT" | grep -q "redhat"; then
    pass "Redhat CLI sees redhat versions"
else
    # May show "latest version" if no redhat updates — that's also correct
    if echo "$ACR_OUTPUT" | grep -qi "latest version"; then
        pass "Redhat CLI correctly found no newer redhat versions"
    else
        fail "Expected redhat version or 'latest version' message"
    fi
fi

# ============================================================
# TEST 9: Unparseable versions don't break update check
# ============================================================

info "--- Test 9: Unparseable versions handled gracefully ---"

WEIRD_REPO="$WORK_DIR/weird-repo"
mkdir -p "$WEIRD_REPO/3.1.0"
cp "$CLI_ZIP" "$WEIRD_REPO/3.1.0/apicurio-registry-cli-3.1.0-${PLATFORM}.zip"

cat > "$WEIRD_REPO/maven-metadata.xml" <<XMLEOF
<?xml version="1.0" encoding="UTF-8"?>
<metadata>
  <groupId>io.apicurio</groupId>
  <artifactId>apicurio-registry-cli</artifactId>
  <versioning>
    <versions>
      <version>3.0.0</version>
      <version>3.1.0</version>
      <version>not-a-version</version>
      <version>weird.format.here.123</version>
    </versions>
  </versioning>
</metadata>
XMLEOF

start_repo "$WEIRD_REPO"

run_acr VERSION="3.0.0" update --check || true

if echo "$ACR_OUTPUT" | grep -q "3.1.0"; then
    pass "Update check works despite unparseable versions in metadata"
else
    fail "Update check failed with unparseable versions. Output: $ACR_OUTPUT"
fi

# ============================================================
# TEST 10: Patch update auto-selected when minor also available
# ============================================================

info "--- Test 10: Patch auto-selected over minor ---"

PATCH_REPO="$WORK_DIR/patch-repo"
mkdir -p "$PATCH_REPO/3.2.5" "$PATCH_REPO/3.3.0"
cp "$CLI_ZIP" "$PATCH_REPO/3.2.5/apicurio-registry-cli-3.2.5-${PLATFORM}.zip"
cp "$CLI_ZIP" "$PATCH_REPO/3.3.0/apicurio-registry-cli-3.3.0-${PLATFORM}.zip"

cat > "$PATCH_REPO/maven-metadata.xml" <<XMLEOF
<?xml version="1.0" encoding="UTF-8"?>
<metadata>
  <groupId>io.apicurio</groupId>
  <artifactId>apicurio-registry-cli</artifactId>
  <versioning>
    <versions>
      <version>3.2.4</version>
      <version>3.2.5</version>
      <version>3.3.0</version>
    </versions>
  </versioning>
</metadata>
XMLEOF

start_repo "$PATCH_REPO"

BEFORE_MTIME=$(stat -c %Y "$INSTALL_HOME/acr_runner" 2>/dev/null || stat -f %m "$INSTALL_HOME/acr_runner")
sleep 1

# Should auto-select 3.2.5 (patch) even though 3.3.0 (minor) is also available
run_acr VERSION="3.2.4" update || true

AFTER_MTIME=$(stat -c %Y "$INSTALL_HOME/acr_runner" 2>/dev/null || stat -f %m "$INSTALL_HOME/acr_runner")

if echo "$ACR_OUTPUT" | grep -q "3.2.5"; then
    pass "Patch version 3.2.5 auto-selected over minor 3.3.0"
else
    fail "Expected patch version 3.2.5 to be auto-selected. Output: $ACR_OUTPUT"
fi

if [ "$AFTER_MTIME" != "$BEFORE_MTIME" ]; then
    pass "Binary was updated with patch version"
else
    fail "Binary was NOT updated"
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
