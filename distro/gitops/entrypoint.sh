#!/usr/bin/env bash
#
# Apicurio Registry GitOps Sidecar Entrypoint
#
# Manages a local Git repository clone for the Apicurio Registry GitOps storage.
# Supports both pull mode (periodic fetch from remote) and push mode (SSH server
# accepting pushes). The registry container reads from a shared volume.
#
# Configuration via environment variables (matching registry property names where applicable):
#
#   APICURIO_GITOPS_WORKSPACE          Base directory for repos (default: /repos)
#   APICURIO_GITOPS_REPO_DIR           Repository directory name (default: default)
#   APICURIO_GITOPS_REPO_BRANCH        Branch to track (default: main)
#   APICURIO_GITOPS_REPO_URL           Remote repository URL (pull mode, required if pull enabled)
#   APICURIO_GITOPS_PULL_ENABLED       Enable periodic pulling (default: true)
#   APICURIO_GITOPS_PULL_INTERVAL      Seconds between fetches (default: 30)
#   APICURIO_GITOPS_PULL_DEPTH         Clone/fetch depth, 0 = full (default: 1)
#   APICURIO_GITOPS_PUSH_ENABLED       Enable SSH server for push (default: false)
#                                      NOTE: Push mode is experimental and not yet fully supported.
#                                      Do not use in production.
#   APICURIO_GITOPS_PUSH_PORT          SSH server port (default: 2222)
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
REPO_DIR="${APICURIO_GITOPS_REPO_DIR:-default}"
BRANCH="${APICURIO_GITOPS_REPO_BRANCH:-main}"
REPO_URL="${APICURIO_GITOPS_REPO_URL:-}"
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

REPO_PATH="${WORKSPACE}/${REPO_DIR}"
TEMPLATE_DIR="/usr/local/share/apicurio-gitops"

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
# Security validation
# ---------------------------------------------------------------------------

validate_security_level() {
    case "${SECURITY}" in
        dev|default|strict) ;;
        *) error "Invalid APICURIO_GITOPS_SECURITY value '${SECURITY}'. Must be 'dev', 'default', or 'strict'." ;;
    esac

    log "Security level: ${SECURITY}"
}

validate_repo_url() {
    if [ -z "${REPO_URL}" ]; then
        return
    fi
    # In default and strict modes, reject plaintext HTTP URLs (credentials could leak)
    if ! is_dev && echo "${REPO_URL}" | grep -qiE '^http://'; then
        error "Plaintext HTTP repository URLs are not allowed in ${SECURITY} mode. Use HTTPS or SSH, or set APICURIO_GITOPS_SECURITY=dev."
    fi
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
    if [ -d "${REPO_PATH}/.git" ]; then
        log "Repository already exists at ${REPO_PATH}"
        return 0
    fi

    if [ "${PULL_ENABLED}" = "true" ]; then
        if [ -z "${REPO_URL}" ]; then
            error "APICURIO_GITOPS_REPO_URL is required when pull mode is enabled"
        fi

        log "Cloning ${REPO_URL} (branch: ${BRANCH}) into ${REPO_PATH}..."
        local depth_args=""
        if [ "${PULL_DEPTH}" -gt 0 ] 2>/dev/null; then
            depth_args="--depth ${PULL_DEPTH}"
        fi
        # shellcheck disable=SC2086
        git clone ${depth_args} --branch "${BRANCH}" --single-branch \
            "${REPO_URL}" "${REPO_PATH}"
        success "Clone complete"
    else
        # Push-only mode: init a bare-like repo that can receive pushes
        log "Initializing repository at ${REPO_PATH} for push mode..."
        mkdir -p "${REPO_PATH}"
        git init --initial-branch="${BRANCH}" "${REPO_PATH}"
        # Configure to allow receiving pushes to the checked-out branch
        git -C "${REPO_PATH}" config receive.denyCurrentBranch updateInstead
        success "Repository initialized for push mode"
    fi
}

pull_once() {
    if [ ! -d "${REPO_PATH}/.git" ]; then
        warning "Repository not found at ${REPO_PATH}, skipping pull"
        return 1
    fi

    local fetch_args=""
    if [ "${PULL_DEPTH}" -gt 0 ] 2>/dev/null; then
        fetch_args="--depth ${PULL_DEPTH}"
    fi

    # Fetch and reset to remote branch tip
    # shellcheck disable=SC2086
    if git -C "${REPO_PATH}" fetch ${fetch_args} origin "${BRANCH}" 2>&1; then
        git -C "${REPO_PATH}" reset --hard "origin/${BRANCH}" >/dev/null 2>&1
        return 0
    else
        warning "Fetch failed"
        return 1
    fi
}

pull_loop() {
    log "Starting pull loop (interval: ${PULL_INTERVAL}s, branch: ${BRANCH})"
    while true; do
        sleep "${PULL_INTERVAL}"
        pull_once || true
    done
}

# ---------------------------------------------------------------------------
# Signal handling
# ---------------------------------------------------------------------------

cleanup() {
    log "Shutting down..."
    if [ -n "${SSHD_PID:-}" ]; then
        kill "${SSHD_PID}" 2>/dev/null || true
    fi
    if [ -n "${PULL_PID:-}" ]; then
        kill "${PULL_PID}" 2>/dev/null || true
    fi
    exit 0
}

trap cleanup SIGTERM SIGINT

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

main() {
    log "Apicurio Registry GitOps sidecar starting"
    log "  Workspace: ${WORKSPACE}"
    log "  Repo dir:  ${REPO_DIR}"
    log "  Branch:    ${BRANCH}"
    log "  Pull:      ${PULL_ENABLED} (interval: ${PULL_INTERVAL}s, depth: ${PULL_DEPTH})"
    log "  Push:      ${PUSH_ENABLED} (port: ${PUSH_PORT})"

    validate_security_level
    validate_repo_url

    mkdir -p "${WORKSPACE}"

    # Set up SSH client if key is provided (for pull authentication)
    if [ -n "${SSH_KEY}" ] || [ -n "${SSH_KNOWN_HOSTS}" ]; then
        setup_ssh_client
    elif is_strict && [ "${PULL_ENABLED}" = "true" ] && echo "${REPO_URL}" | grep -qiE '^(git@|ssh://)'; then
        error "APICURIO_GITOPS_SSH_KNOWN_HOSTS is required in strict mode when using SSH repository URLs."
    fi

    # Initialize the repository
    init_repo

    # Start push mode (SSH server) if enabled
    if [ "${PUSH_ENABLED}" = "true" ]; then
        setup_ssh_server
        start_ssh_server
    fi

    # Start pull loop if enabled
    if [ "${PULL_ENABLED}" = "true" ]; then
        pull_loop &
        PULL_PID=$!
        log "Pull loop started (PID: ${PULL_PID})"
    fi

    # Wait for any background process to exit
    if [ -n "${SSHD_PID:-}" ]; then
        wait "${SSHD_PID}" || true
    elif [ -n "${PULL_PID:-}" ]; then
        wait "${PULL_PID}" || true
    else
        # Neither pull nor push enabled — just keep running
        warning "Neither pull nor push mode is enabled. Container will idle."
        tail -f /dev/null &
        wait $!
    fi
}

main "$@"
