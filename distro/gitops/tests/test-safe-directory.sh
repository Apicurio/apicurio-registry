#!/usr/bin/env bash
#
# Unit tests for configure_git_safe_directories() in entrypoint.sh.
# Run: bash distro/gitops/tests/test-safe-directory.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENTRYPOINT="${SCRIPT_DIR}/../entrypoint.sh"

if [[ ! -f "${ENTRYPOINT}" ]]; then
    echo "ERROR: entrypoint not found: ${ENTRYPOINT}" >&2
    exit 1
fi

# Load only the function under test to avoid running sidecar startup side effects.
eval "$(sed -n '/^configure_git_safe_directories()/,/^}/p' "${ENTRYPOINT}")"

if ! declare -F configure_git_safe_directories >/dev/null; then
    echo "ERROR: failed to load configure_git_safe_directories from ${ENTRYPOINT}" >&2
    exit 1
fi

failures=0

assert_eq() {
    local name="$1" expected="$2" actual="$3"
    if [[ "${expected}" != "${actual}" ]]; then
        echo "FAIL: ${name}: expected='${expected}' actual='${actual}'" >&2
        failures=$((failures + 1))
        return 1
    fi
    echo "PASS: ${name}"
}

reset_git_config_env() {
    unset GIT_CONFIG_COUNT || true
    local i
    for i in $(seq 0 20); do
        unset "GIT_CONFIG_KEY_${i}" "GIT_CONFIG_VALUE_${i}" 2>/dev/null || true
    done
}

# --- workspace + named repo ---
reset_git_config_env
WORKSPACE="/repos"
REPO_DIRS=("default")
configure_git_safe_directories

assert_eq "named-repo count" "2" "${GIT_CONFIG_COUNT}"
assert_eq "named-repo key0" "safe.directory" "${GIT_CONFIG_KEY_0}"
assert_eq "named-repo val0" "/repos" "${GIT_CONFIG_VALUE_0}"
assert_eq "named-repo key1" "safe.directory" "${GIT_CONFIG_KEY_1}"
assert_eq "named-repo val1" "/repos/default" "${GIT_CONFIG_VALUE_1}"

# --- dir=. must not duplicate the workspace entry ---
reset_git_config_env
WORKSPACE="/repos/"
REPO_DIRS=(".")
configure_git_safe_directories

assert_eq "dot-dir count" "1" "${GIT_CONFIG_COUNT}"
assert_eq "dot-dir key0" "safe.directory" "${GIT_CONFIG_KEY_0}"
assert_eq "dot-dir val0" "/repos" "${GIT_CONFIG_VALUE_0}"

# --- multi-repo + preserve pre-existing GIT_CONFIG_* ---
reset_git_config_env
export GIT_CONFIG_COUNT=1
export GIT_CONFIG_KEY_0="user.name"
export GIT_CONFIG_VALUE_0="test"
WORKSPACE="/data"
REPO_DIRS=("a" "b")
configure_git_safe_directories

assert_eq "preserve count" "4" "${GIT_CONFIG_COUNT}"
assert_eq "preserve key0" "user.name" "${GIT_CONFIG_KEY_0}"
assert_eq "preserve val0" "test" "${GIT_CONFIG_VALUE_0}"
assert_eq "preserve val1" "/data" "${GIT_CONFIG_VALUE_1}"
assert_eq "preserve val2" "/data/a" "${GIT_CONFIG_VALUE_2}"
assert_eq "preserve val3" "/data/b" "${GIT_CONFIG_VALUE_3}"

# --- empty WORKSPACE normalizes to / ---
reset_git_config_env
WORKSPACE=""
REPO_DIRS=("default")
configure_git_safe_directories

assert_eq "empty-workspace val0" "/" "${GIT_CONFIG_VALUE_0}"
assert_eq "empty-workspace val1" "/default" "${GIT_CONFIG_VALUE_1}"

if [[ "${failures}" -ne 0 ]]; then
    echo "FAILED: ${failures} assertion(s)" >&2
    exit 1
fi

echo "All safe.directory tests passed."
