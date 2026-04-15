#!/usr/bin/env bash
#
# Apicurio Registry GitOps Sidecar Entrypoint
#
# Manages local Git repository clones for the Apicurio Registry GitOps storage.
# Supports both pull mode (periodic fetch from remote) and push mode (SSH server
# accepting pushes). The registry container reads from a shared volume.
#
# Configuration via environment variables (matching registry property names where applicable):
#
# Shared with registry:
#   APICURIO_GITOPS_WORKSPACE          Base directory for repos (default: /repos)
#
# Single-repo shorthand:
#   APICURIO_GITOPS_REPO_DIR           Repository directory name (default: default)
#   APICURIO_GITOPS_REPO_BRANCH        Branch to track (default: main)
#   APICURIO_GITOPS_REPO_URL           Remote repository URL (pull mode, required if pull enabled)
#
# Multi-repo (indexed, takes precedence over single-repo):
#   APICURIO_GITOPS_REPOS_0_DIR        First repo directory name
#   APICURIO_GITOPS_REPOS_0_BRANCH     First repo branch (default: main)
#   APICURIO_GITOPS_REPOS_0_URL        First repo remote URL
#   APICURIO_GITOPS_REPOS_1_DIR        Second repo directory name
#   ... and so on
#
# Pull/push mode:
#   APICURIO_GITOPS_PULL_ENABLED       Enable periodic pulling (default: true)
#   APICURIO_GITOPS_PULL_INTERVAL      Seconds between fetches (default: 30)
#   APICURIO_GITOPS_PULL_DEPTH         Clone/fetch depth, 0 = full (default: 1)
#   APICURIO_GITOPS_PUSH_ENABLED       Enable SSH server for push (default: false)
#                                      NOTE: Push mode is experimental and not yet fully supported.
#                                      Do not use in production.
#   APICURIO_GITOPS_PUSH_PORT          SSH server port (default: 2222)
#
# SSH configuration:
#   APICURIO_GITOPS_SSH_KEY            Path to SSH private key for pulling (optional)
#   APICURIO_GITOPS_SSH_KNOWN_HOSTS    Path to known_hosts file (optional)
#   APICURIO_GITOPS_SSH_AUTHORIZED_KEYS Path to authorized_keys for push mode (optional)
#   APICURIO_GITOPS_SSH_HOST_KEY       Path to SSH host private key for push mode (optional)
#   APICURIO_GITOPS_SECURITY           Security level (default: default)
#                                      dev     - auto-generate keys, print private keys to log
#                                      default - auto-generate keys, print fingerprints only, TOFU,
#                                                reject HTTP URLs
#                                      strict  - require all keys to be mounted, reject HTTP URLs
#

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

WORKSPACE="${APICURIO_GITOPS_WORKSPACE:-/repos}"
PULL_ENABLED="${APICURIO_GITOPS_PULL_ENABLED:-true}"
PULL_INTERVAL="${APICURIO_GITOPS_PULL_INTERVAL:-30}"
PULL_DEPTH="${APICURIO_GITOPS_PULL_DEPTH:-1}"
PUSH_ENABLED="${APICURIO_GITOPS_PUSH_ENABLED:-false}"
PUSH_PORT="${APICURIO_GITOPS_PUSH_PORT:-2222}"
SSH_KEY="${APICURIO_GITOPS_SSH_KEY:-}"
SSH_KNOWN_HOSTS="${APICURIO_GITOPS_SSH_KNOWN_HOSTS:-}"
SSH_AUTHORIZED_KEYS="${APICURIO_GITOPS_SSH_AUTHORIZED_KEYS:-}"
SSH_HOST_KEY="${APICURIO_GITOPS_SSH_HOST_KEY:-}"
SECURITY="${APICURIO_GITOPS_SECURITY:-default}"

TEMPLATE_DIR="/usr/local/share/apicurio-gitops"

# Arrays to hold per-repo configuration (populated by build_repo_list)
REPO_DIRS=()
REPO_BRANCHES=()
REPO_URLS=()

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------

_RED='\033[0;31m'
_GREEN='\033[0;32m'
_YELLOW='\033[0;33m'
_CYAN='\033[0;36m'
_RESET='\033[0m'

_timestamp() {
    date -u '+%Y-%m-%dT%H:%M:%SZ'
}

log() {
    echo -e "${_CYAN}[$(_timestamp)]${_RESET} $*"
}

success() {
    echo -e "${_CYAN}[$(_timestamp)]${_RESET} ${_GREEN}$*${_RESET}"
}

warning() {
    echo -e "${_CYAN}[$(_timestamp)]${_RESET} ${_YELLOW}WARNING: $*${_RESET}"
}

error() {
    echo -e "${_CYAN}[$(_timestamp)]${_RESET} ${_RED}ERROR: $*${_RESET}" >&2
    exit 1
}

# ---------------------------------------------------------------------------
# Security helpers
# ---------------------------------------------------------------------------

is_dev() {
    [ "${SECURITY}" = "dev" ]
}

is_strict() {
    [ "${SECURITY}" = "strict" ]
}

# Render a template file by replacing {{VAR}} placeholders with shell variables.
# Usage: render_template <template_file> <output_file> VAR1=value1 VAR2=value2 ...
render_template() {
    local template="$1"
    local output="$2"
    shift 2

    if [ ! -f "${template}" ]; then
        error "Template file not found: ${template}"
    fi

    local content
    content=$(cat "${template}")

    local pair
    for pair in "$@"; do
        local key="${pair%%=*}"
        local value="${pair#*=}"
        local placeholder="{{${key}}}"
        content="${content//${placeholder}/${value}}"
    done

    printf '%s' "${content}" > "${output}"
}

# ---------------------------------------------------------------------------
# Repo list construction
# ---------------------------------------------------------------------------

build_repo_list() {
    # Try indexed format: APICURIO_GITOPS_REPOS_0_DIR, APICURIO_GITOPS_REPOS_1_DIR, ...
    # Same single-underscore format used by both the sidecar and registry.
    local i=0
    while true; do
        local dir_var="APICURIO_GITOPS_REPOS_${i}_DIR"
        local branch_var="APICURIO_GITOPS_REPOS_${i}_BRANCH"
        local url_var="APICURIO_GITOPS_REPOS_${i}_URL"

        local dir="${!dir_var:-}"
        if [ -z "${dir}" ]; then
            # Require dense indexes — no gaps allowed
            local next_var="APICURIO_GITOPS_REPOS_$((i + 1))_DIR"
            if [ -n "${!next_var:-}" ]; then
                error "Missing repo at index ${i} but index $((i + 1)) is set. Indexes must be dense (no gaps)."
            fi
            break
        fi

        REPO_DIRS+=("${dir}")
        REPO_BRANCHES+=("${!branch_var:-main}")
        REPO_URLS+=("${!url_var:-}")
        i=$((i + 1))
    done

    local has_shorthand=false
    if [ -n "${APICURIO_GITOPS_REPO_DIR:-}" ] || [ -n "${APICURIO_GITOPS_REPO_BRANCH:-}" ] || [ -n "${APICURIO_GITOPS_REPO_URL:-}" ]; then
        has_shorthand=true
    fi

    if [ ${#REPO_DIRS[@]} -gt 0 ] && [ "${has_shorthand}" = "true" ]; then
        error "Cannot use both single-repo (APICURIO_GITOPS_REPO_DIR/BRANCH/URL) and multi-repo (APICURIO_GITOPS_REPOS_N_*) configuration. Use one or the other."
    fi

    # Fall back to single-repo shorthand if no indexed repos found
    if [ ${#REPO_DIRS[@]} -eq 0 ]; then
        REPO_DIRS+=("${APICURIO_GITOPS_REPO_DIR:-default}")
        REPO_BRANCHES+=("${APICURIO_GITOPS_REPO_BRANCH:-main}")
        REPO_URLS+=("${APICURIO_GITOPS_REPO_URL:-}")
    fi

    validate_repo_list
}

validate_repo_list() {
    local seen_dirs=""
    local seen_dir_branches=""

    for i in "${!REPO_DIRS[@]}"; do
        local dir="${REPO_DIRS[$i]}"
        local branch="${REPO_BRANCHES[$i]}"
        local dir_branch="${dir}:${branch}"

        # Check for duplicate directory names (used as repo IDs)
        if echo "${seen_dirs}" | grep -qF "|${dir}|"; then
            error "Duplicate repository directory: '${dir}'. Each repository must have a unique directory name."
        fi
        seen_dirs="${seen_dirs}|${dir}|"

        # Check for duplicate dir+branch combinations
        if echo "${seen_dir_branches}" | grep -qF "|${dir_branch}|"; then
            error "Duplicate repository: dir='${dir}', branch='${branch}'. The same directory and branch combination cannot be configured twice."
        fi
        seen_dir_branches="${seen_dir_branches}|${dir_branch}|"
    done
}

# ---------------------------------------------------------------------------
# Security validation
# ---------------------------------------------------------------------------

validate_security_level() {
    case "${SECURITY}" in
        dev|default|strict) ;;
        *) error "Invalid APICURIO_GITOPS_SECURITY value '${SECURITY}'. Must be 'dev', 'default', or 'strict'." ;;
    esac

    log "Security level: ${SECURITY}"
}

validate_repo_urls() {
    for url in "${REPO_URLS[@]}"; do
        if [ -n "${url}" ] && ! is_dev && echo "${url}" | grep -qiE '^http://'; then
            error "Plaintext HTTP repository URLs are not allowed in ${SECURITY} mode. Use HTTPS or SSH, or set APICURIO_GITOPS_SECURITY=dev."
        fi
    done
}

validate_ssh_key_permissions() {
    local key_path="$1"
    if [ ! -f "${key_path}" ]; then
        return
    fi
    local perms
    perms=$(stat -c '%a' "${key_path}" 2>/dev/null || stat -f '%Lp' "${key_path}" 2>/dev/null)
    if [ "${perms}" != "600" ] && [ "${perms}" != "400" ]; then
        if is_strict; then
            error "SSH key ${key_path} has permissions ${perms}, expected 600 or 400. Fix permissions or use a properly configured secret mount."
        else
            warning "SSH key ${key_path} has permissions ${perms} (expected 600 or 400)"
        fi
    fi
}

# ---------------------------------------------------------------------------
# SSH client setup (for pull mode authentication)
# ---------------------------------------------------------------------------

setup_ssh_client() {
    local ssh_dir="${HOME}/.ssh"
    mkdir -p "${ssh_dir}"
    chmod 700 "${ssh_dir}"

    if [ -n "${SSH_KEY}" ]; then
        if [ ! -f "${SSH_KEY}" ]; then
            error "SSH key file not found: ${SSH_KEY}"
        fi
        validate_ssh_key_permissions "${SSH_KEY}"
        # Copy key so we can set permissions without affecting the mounted secret
        cp "${SSH_KEY}" "${ssh_dir}/id_key"
        chmod 600 "${ssh_dir}/id_key"
        success "SSH client key configured"
    fi

    if [ -n "${SSH_KNOWN_HOSTS}" ]; then
        if [ ! -f "${SSH_KNOWN_HOSTS}" ]; then
            error "known_hosts file not found: ${SSH_KNOWN_HOSTS}"
        fi
        cp "${SSH_KNOWN_HOSTS}" "${ssh_dir}/known_hosts"
        chmod 644 "${ssh_dir}/known_hosts"
        success "known_hosts configured"
    elif is_strict; then
        error "APICURIO_GITOPS_SSH_KNOWN_HOSTS is required in strict mode. Provide a known_hosts file to prevent MITM attacks."
    fi

    # Determine host key checking policy
    local strict_host_key_checking="accept-new"
    if [ -n "${SSH_KNOWN_HOSTS}" ]; then
        strict_host_key_checking="yes"
    fi

    # Render SSH client config from template
    render_template "${TEMPLATE_DIR}/ssh_config.template" "${ssh_dir}/config" \
        "STRICT_HOST_KEY_CHECKING=${strict_host_key_checking}" \
        "SSH_DIR=${ssh_dir}"

    if [ -f "${ssh_dir}/id_key" ]; then
        echo "    IdentityFile ${ssh_dir}/id_key" >> "${ssh_dir}/config"
    fi

    chmod 600 "${ssh_dir}/config"

    # Set GIT_SSH_COMMAND so git uses our config
    export GIT_SSH_COMMAND="ssh -F ${ssh_dir}/config"
}

# ---------------------------------------------------------------------------
# SSH server setup (for push mode)
# ---------------------------------------------------------------------------

setup_ssh_host_keys() {
    local sshd_dir="$1"

    if [ -n "${SSH_HOST_KEY}" ]; then
        # Use mounted host key
        if [ ! -f "${SSH_HOST_KEY}" ]; then
            error "SSH host key file not found: ${SSH_HOST_KEY}"
        fi
        validate_ssh_key_permissions "${SSH_HOST_KEY}"
        cp "${SSH_HOST_KEY}" "${sshd_dir}/ssh_host_key"
        chmod 600 "${sshd_dir}/ssh_host_key"

        # Also copy the public key if it exists alongside the private key
        if [ -f "${SSH_HOST_KEY}.pub" ]; then
            cp "${SSH_HOST_KEY}.pub" "${sshd_dir}/ssh_host_key.pub"
        fi
        success "Using mounted SSH host key"
        return
    fi

    # No host key mounted
    if is_strict; then
        error "APICURIO_GITOPS_SSH_HOST_KEY is required in strict mode. Mount an SSH host key to ensure stable host identity."
    fi

    # Dev/default mode: auto-generate host keys
    if [ ! -f "${sshd_dir}/ssh_host_ed25519_key" ]; then
        ssh-keygen -t ed25519 -f "${sshd_dir}/ssh_host_ed25519_key" -N "" -q
        log "Generated ED25519 host key"
    fi
    if [ ! -f "${sshd_dir}/ssh_host_rsa_key" ]; then
        ssh-keygen -t rsa -b 4096 -f "${sshd_dir}/ssh_host_rsa_key" -N "" -q
        log "Generated RSA host key"
    fi

    if is_dev; then
        log "--- BEGIN SSH HOST PRIVATE KEY (dev mode) ---"
        cat "${sshd_dir}/ssh_host_ed25519_key"
        log "--- END SSH HOST PRIVATE KEY (dev mode) ---"
        log "Host public key:"
        cat "${sshd_dir}/ssh_host_ed25519_key.pub"
    else
        log "Host key fingerprint:"
        ssh-keygen -lf "${sshd_dir}/ssh_host_ed25519_key.pub"
    fi
}

setup_ssh_server() {
    local sshd_dir="/home/git/.sshd"
    mkdir -p "${sshd_dir}"

    setup_ssh_host_keys "${sshd_dir}"

    # Determine which HostKey directives to use
    local host_key_directives=""
    if [ -f "${sshd_dir}/ssh_host_key" ]; then
        host_key_directives="HostKey ${sshd_dir}/ssh_host_key"
    else
        host_key_directives="HostKey ${sshd_dir}/ssh_host_ed25519_key
HostKey ${sshd_dir}/ssh_host_rsa_key"
    fi

    # Set up authorized keys
    local auth_keys_dir="/home/git/.ssh"
    mkdir -p "${auth_keys_dir}"
    chmod 700 "${auth_keys_dir}"

    if [ -n "${SSH_AUTHORIZED_KEYS}" ] && [ -f "${SSH_AUTHORIZED_KEYS}" ]; then
        cp "${SSH_AUTHORIZED_KEYS}" "${auth_keys_dir}/authorized_keys"
        chmod 600 "${auth_keys_dir}/authorized_keys"
        success "Authorized keys configured for push access"
    elif is_strict; then
        error "APICURIO_GITOPS_SSH_AUTHORIZED_KEYS is required in strict mode. Provide an authorized_keys file to control push access."
    else
        warning "Push mode enabled but no authorized keys configured (APICURIO_GITOPS_SSH_AUTHORIZED_KEYS)"
    fi

    # Render sshd config from template
    render_template "${TEMPLATE_DIR}/sshd_config.template" "${sshd_dir}/sshd_config" \
        "PUSH_PORT=${PUSH_PORT}" \
        "HOST_KEY_DIRECTIVES=${host_key_directives}" \
        "AUTH_KEYS_DIR=${auth_keys_dir}" \
        "SSHD_DIR=${sshd_dir}"

    success "SSH server configured on port ${PUSH_PORT}"
}

start_ssh_server() {
    local sshd_dir="/home/git/.sshd"
    log "Starting SSH server on port ${PUSH_PORT}..."
    /usr/sbin/sshd -f "${sshd_dir}/sshd_config" -e &
    SSHD_PID=$!
    success "SSH server started (PID: ${SSHD_PID})"
}

# ---------------------------------------------------------------------------
# Git repository management
# ---------------------------------------------------------------------------

init_repo() {
    local repo_dir="$1"
    local repo_branch="$2"
    local repo_url="$3"
    local repo_path="${WORKSPACE}/${repo_dir}"

    if [ -d "${repo_path}/.git" ]; then
        log "[${repo_dir}] Repository already exists"
        return 0
    fi

    if [ "${PULL_ENABLED}" = "true" ]; then
        if [ -z "${repo_url}" ]; then
            error "[${repo_dir}] Repository URL is required when pull mode is enabled"
        fi

        log "[${repo_dir}] Cloning ${repo_url} (branch: ${repo_branch})..."
        local depth_args=""
        if [ "${PULL_DEPTH}" -gt 0 ] 2>/dev/null; then
            depth_args="--depth ${PULL_DEPTH}"
        fi
        # shellcheck disable=SC2086
        git clone ${depth_args} --branch "${repo_branch}" --single-branch \
            "${repo_url}" "${repo_path}"
        success "[${repo_dir}] Clone complete"
    else
        # Push-only mode: init a bare-like repo that can receive pushes
        log "[${repo_dir}] Initializing for push mode..."
        mkdir -p "${repo_path}"
        git init --initial-branch="${repo_branch}" "${repo_path}"
        git -C "${repo_path}" config receive.denyCurrentBranch updateInstead
        success "[${repo_dir}] Repository initialized for push mode"
    fi
}

pull_once() {
    local repo_dir="$1"
    local repo_branch="$2"
    local repo_path="${WORKSPACE}/${repo_dir}"

    if [ ! -d "${repo_path}/.git" ]; then
        warning "[${repo_dir}] Repository not found, skipping pull"
        return 1
    fi

    local fetch_args=""
    if [ "${PULL_DEPTH}" -gt 0 ] 2>/dev/null; then
        fetch_args="--depth ${PULL_DEPTH}"
    fi

    # shellcheck disable=SC2086
    if git -C "${repo_path}" fetch ${fetch_args} origin "${repo_branch}" 2>&1; then
        git -C "${repo_path}" reset --hard "origin/${repo_branch}" >/dev/null 2>&1
        return 0
    else
        warning "[${repo_dir}] Fetch failed"
        return 1
    fi
}

pull_loop() {
    log "Starting pull loop (interval: ${PULL_INTERVAL}s, ${#REPO_DIRS[@]} repo(s))"
    while true; do
        sleep "${PULL_INTERVAL}"
        for i in "${!REPO_DIRS[@]}"; do
            pull_once "${REPO_DIRS[$i]}" "${REPO_BRANCHES[$i]}" || true
        done
    done
}

# ---------------------------------------------------------------------------
# Signal handling
# ---------------------------------------------------------------------------

PULL_PIDS=()

cleanup() {
    log "Shutting down..."
    if [ -n "${SSHD_PID:-}" ]; then
        kill "${SSHD_PID}" 2>/dev/null || true
    fi
    for pid in "${PULL_PIDS[@]}"; do
        kill "${pid}" 2>/dev/null || true
    done
    exit 0
}

trap cleanup SIGTERM SIGINT

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

main() {
    log "Apicurio Registry GitOps sidecar starting"

    build_repo_list

    log "  Workspace: ${WORKSPACE}"
    log "  Repos:     ${#REPO_DIRS[@]}"
    for i in "${!REPO_DIRS[@]}"; do
        log "    [${REPO_DIRS[$i]}] branch=${REPO_BRANCHES[$i]} url=${REPO_URLS[$i]:-<local>}"
    done
    log "  Pull:      ${PULL_ENABLED} (interval: ${PULL_INTERVAL}s, depth: ${PULL_DEPTH})"
    log "  Push:      ${PUSH_ENABLED} (port: ${PUSH_PORT})"

    validate_security_level
    validate_repo_urls

    mkdir -p "${WORKSPACE}"

    # Set up SSH client if key is provided (for pull authentication)
    local has_ssh_url=false
    for url in "${REPO_URLS[@]}"; do
        if echo "${url}" | grep -qiE '^(git@|ssh://)'; then
            has_ssh_url=true
            break
        fi
    done

    if [ -n "${SSH_KEY}" ] || [ -n "${SSH_KNOWN_HOSTS}" ]; then
        setup_ssh_client
    elif is_strict && [ "${PULL_ENABLED}" = "true" ] && [ "${has_ssh_url}" = "true" ]; then
        error "APICURIO_GITOPS_SSH_KNOWN_HOSTS is required in strict mode when using SSH repository URLs."
    fi

    # Initialize all repositories
    for i in "${!REPO_DIRS[@]}"; do
        init_repo "${REPO_DIRS[$i]}" "${REPO_BRANCHES[$i]}" "${REPO_URLS[$i]}"
    done

    # Start push mode (SSH server) if enabled
    if [ "${PUSH_ENABLED}" = "true" ]; then
        setup_ssh_server
        start_ssh_server
    fi

    # Start pull loop if enabled
    if [ "${PULL_ENABLED}" = "true" ]; then
        pull_loop &
        PULL_PIDS+=($!)
        log "Pull loop started (PID: ${PULL_PIDS[0]})"
    fi

    # Wait for any background process to exit
    if [ -n "${SSHD_PID:-}" ]; then
        wait "${SSHD_PID}" || true
    elif [ ${#PULL_PIDS[@]} -gt 0 ]; then
        wait "${PULL_PIDS[0]}" || true
    else
        # Neither pull nor push enabled — just keep running
        warning "Neither pull nor push mode is enabled. Container will idle."
        tail -f /dev/null &
        wait $!
    fi
}

main "$@"
