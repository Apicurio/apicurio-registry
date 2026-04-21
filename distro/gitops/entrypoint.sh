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
#   APICURIO_GITOPS_REPOS_0_SSH_KEYS   First repo SSH keys (added alongside global PULL_SSH_KEYS)
#   APICURIO_GITOPS_REPOS_0_MODE       First repo mode (overrides global MODE, default: pull)
#   APICURIO_GITOPS_REPOS_1_DIR        Second repo directory name
#   ... and so on
#
# Mode:
#   APICURIO_GITOPS_MODE               pull (default) or push
#   APICURIO_GITOPS_PULL_INTERVAL      Seconds between fetches (default: 30, pull mode)
#   APICURIO_GITOPS_PULL_DEPTH         Clone/fetch depth, 0 = full (default: 1, pull mode)
#   APICURIO_GITOPS_PUSH_PORT          SSH server port (default: 2222, push mode)
#
# Pull SSH (for authenticating to remote Git servers):
#   APICURIO_GITOPS_PULL_SSH_KEYS            Path to SSH private key, or comma-separated list of paths (optional)
#   APICURIO_GITOPS_PULL_SSH_KNOWN_HOSTS    Path to known_hosts file (optional)
#
# Push SSH (for the push mode SSH server):
#   APICURIO_GITOPS_PUSH_SSH_AUTHORIZED_KEYS Path to authorized_keys (optional)
#   APICURIO_GITOPS_PUSH_SSH_HOST_KEY        Path to SSH host private key (optional)
#
# Security:
#   APICURIO_GITOPS_SECURITY           Security level (default: strict)
#                                      dev    - auto-generate keys, TOFU for host verification,
#                                               warn on issues
#                                      strict - require all keys to be mounted, fail on issues
#

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

WORKSPACE="${APICURIO_GITOPS_WORKSPACE:-/repos}"
MODE="${APICURIO_GITOPS_MODE:-pull}"
PULL_INTERVAL="${APICURIO_GITOPS_PULL_INTERVAL:-30}"
PULL_DEPTH="${APICURIO_GITOPS_PULL_DEPTH:-1}"
PUSH_PORT="${APICURIO_GITOPS_PUSH_PORT:-2222}"
PULL_SSH_KEYS="${APICURIO_GITOPS_PULL_SSH_KEYS:-}"
PULL_SSH_KNOWN_HOSTS="${APICURIO_GITOPS_PULL_SSH_KNOWN_HOSTS:-}"
PUSH_SSH_AUTHORIZED_KEYS="${APICURIO_GITOPS_PUSH_SSH_AUTHORIZED_KEYS:-}"
PUSH_SSH_HOST_KEY="${APICURIO_GITOPS_PUSH_SSH_HOST_KEY:-}"
SECURITY="${APICURIO_GITOPS_SECURITY:-strict}"

TEMPLATE_DIR="/usr/local/share/apicurio-gitops"

# Arrays to hold per-repo configuration (populated by build_repo_list)
REPO_DIRS=()
REPO_BRANCHES=()
REPO_URLS=()
REPO_SSH_KEYS=()
REPO_MODES=()

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

is_strict() {
    [ "${SECURITY}" != "dev" ]
}

is_pull() {
    [ "${MODE}" = "pull" ]
}

is_push() {
    [ "${MODE}" = "push" ]
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
        local ssh_keys_var="APICURIO_GITOPS_REPOS_${i}_SSH_KEYS"
        local mode_var="APICURIO_GITOPS_REPOS_${i}_MODE"

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
        REPO_SSH_KEYS+=("${!ssh_keys_var:-}")
        REPO_MODES+=("${!mode_var:-${MODE}}")
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
        REPO_SSH_KEYS+=("")
        REPO_MODES+=("${MODE}")
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

validate_mode() {
    case "${MODE}" in
        pull|push) ;;
        *) error "Invalid APICURIO_GITOPS_MODE value '${MODE}'. Must be 'pull' or 'push'." ;;
    esac
    for i in "${!REPO_MODES[@]}"; do
        case "${REPO_MODES[$i]}" in
            pull|push) ;;
            *) error "Invalid mode '${REPO_MODES[$i]}' for repo '${REPO_DIRS[$i]}'. Must be 'pull' or 'push'." ;;
        esac
    done
}

validate_security_level() {
    case "${SECURITY}" in
        dev|strict) ;;
        *) error "Invalid APICURIO_GITOPS_SECURITY value '${SECURITY}'. Must be 'dev' or 'strict'." ;;
    esac

    log "Security level: ${SECURITY}"
}

validate_repo_urls() {
    for url in "${REPO_URLS[@]}"; do
        if [ -n "${url}" ] && echo "${url}" | grep -qiE '^http://'; then
            error "Plaintext HTTP repository URLs are not allowed. Use HTTPS or SSH."
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

    # Collect SSH keys: global keys (PULL_SSH_KEYS) and per-repo keys (REPOS_N_SSH_KEYS).
    # All keys are added as IdentityFile entries — SSH tries each in order.
    local key_index=0
    local all_keys="${PULL_SSH_KEYS}"
    for repo_keys in "${REPO_SSH_KEYS[@]}"; do
        if [ -n "${repo_keys}" ]; then
            all_keys="${all_keys:+${all_keys},}${repo_keys}"
        fi
    done

    if [ -n "${all_keys}" ]; then
        local IFS=','
        for key_path in ${all_keys}; do
            key_path=$(echo "${key_path}" | xargs)  # trim whitespace
            if [ ! -f "${key_path}" ]; then
                error "SSH key file not found: ${key_path}"
            fi
            validate_ssh_key_permissions "${key_path}"
            cp "${key_path}" "${ssh_dir}/id_key_${key_index}"
            chmod 600 "${ssh_dir}/id_key_${key_index}"
            key_index=$((key_index + 1))
        done
        success "${key_index} SSH client key(s) configured"
    fi

    if [ -n "${PULL_SSH_KNOWN_HOSTS}" ]; then
        if [ ! -f "${PULL_SSH_KNOWN_HOSTS}" ]; then
            error "known_hosts file not found: ${PULL_SSH_KNOWN_HOSTS}"
        fi
        cp "${PULL_SSH_KNOWN_HOSTS}" "${ssh_dir}/known_hosts"
        chmod 644 "${ssh_dir}/known_hosts"
        success "known_hosts configured"
    fi

    # Determine host key checking policy
    local strict_host_key_checking="accept-new"
    if [ -n "${PULL_SSH_KNOWN_HOSTS}" ]; then
        strict_host_key_checking="yes"
    fi

    # Render SSH client config from template
    render_template "${TEMPLATE_DIR}/ssh_config.template" "${ssh_dir}/config" \
        "STRICT_HOST_KEY_CHECKING=${strict_host_key_checking}" \
        "SSH_DIR=${ssh_dir}"

    # Add all copied keys as IdentityFile entries
    for key_file in "${ssh_dir}"/id_key_*; do
        [ -f "${key_file}" ] && echo "    IdentityFile ${key_file}" >> "${ssh_dir}/config"
    done

    chmod 600 "${ssh_dir}/config"

    # Set GIT_SSH_COMMAND so git uses our config
    export GIT_SSH_COMMAND="ssh -F ${ssh_dir}/config"
}

# ---------------------------------------------------------------------------
# SSH server setup (for push mode)
# ---------------------------------------------------------------------------

setup_ssh_host_keys() {
    local sshd_dir="$1"

    if [ -n "${PUSH_SSH_HOST_KEY}" ]; then
        # Use mounted host key
        if [ ! -f "${PUSH_SSH_HOST_KEY}" ]; then
            error "SSH host key file not found: ${PUSH_SSH_HOST_KEY}"
        fi
        validate_ssh_key_permissions "${PUSH_SSH_HOST_KEY}"
        cp "${PUSH_SSH_HOST_KEY}" "${sshd_dir}/ssh_host_key"
        chmod 600 "${sshd_dir}/ssh_host_key"

        # Also copy the public key if it exists alongside the private key
        if [ -f "${PUSH_SSH_HOST_KEY}.pub" ]; then
            cp "${PUSH_SSH_HOST_KEY}.pub" "${sshd_dir}/ssh_host_key.pub"
        fi
        success "Using mounted SSH host key"
        return
    fi

    # No host key mounted
    if is_strict; then
        error "APICURIO_GITOPS_PUSH_SSH_HOST_KEY is required in strict mode. Mount an SSH host key to ensure stable host identity."
    fi

    # Dev mode: auto-generate host keys
    if [ ! -f "${sshd_dir}/ssh_host_ed25519_key" ]; then
        ssh-keygen -t ed25519 -f "${sshd_dir}/ssh_host_ed25519_key" -N "" -q
        log "Generated ED25519 host key"
    fi
    if [ ! -f "${sshd_dir}/ssh_host_rsa_key" ]; then
        ssh-keygen -t rsa -b 4096 -f "${sshd_dir}/ssh_host_rsa_key" -N "" -q
        log "Generated RSA host key"
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

    if [ -n "${PUSH_SSH_AUTHORIZED_KEYS}" ] && [ -f "${PUSH_SSH_AUTHORIZED_KEYS}" ]; then
        cp "${PUSH_SSH_AUTHORIZED_KEYS}" "${auth_keys_dir}/authorized_keys"
        chmod 600 "${auth_keys_dir}/authorized_keys"
        success "Authorized keys configured for push access"
    elif is_strict; then
        error "APICURIO_GITOPS_PUSH_SSH_AUTHORIZED_KEYS is required in strict mode. Provide an authorized_keys file to control push access."
    else
        warning "Push mode enabled but no authorized keys configured (APICURIO_GITOPS_PUSH_SSH_AUTHORIZED_KEYS)"
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
    /usr/sbin/sshd -D -f "${sshd_dir}/sshd_config" -e &
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
    local repo_mode="$4"
    local repo_path="${WORKSPACE}/${repo_dir}"

    if [ -d "${repo_path}/.git" ]; then
        log "[${repo_dir}] Repository already exists"
        return 0
    fi

    if [ "${repo_mode}" = "pull" ]; then
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
    log "Starting pull loop (interval: ${PULL_INTERVAL}s)"
    while true; do
        sleep "${PULL_INTERVAL}"
        for i in "${!REPO_DIRS[@]}"; do
            if [ "${REPO_MODES[$i]}" = "pull" ]; then
                pull_once "${REPO_DIRS[$i]}" "${REPO_BRANCHES[$i]}" || true
            fi
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
        log "    [${REPO_DIRS[$i]}] mode=${REPO_MODES[$i]} branch=${REPO_BRANCHES[$i]} url=${REPO_URLS[$i]:-<local>}"
    done

    validate_mode
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

    # Determine which modes are in use across all repos
    local has_pull=false has_push=false
    for mode in "${REPO_MODES[@]}"; do
        case "${mode}" in
            pull) has_pull=true ;;
            push) has_push=true ;;
        esac
    done

    # Check if any SSH keys are configured (global or per-repo)
    local has_any_ssh_keys=false
    if [ -n "${PULL_SSH_KEYS}" ] || [ -n "${PULL_SSH_KNOWN_HOSTS}" ]; then
        has_any_ssh_keys=true
    else
        for repo_keys in "${REPO_SSH_KEYS[@]}"; do
            if [ -n "${repo_keys}" ]; then
                has_any_ssh_keys=true
                break
            fi
        done
    fi

    if [ "${has_any_ssh_keys}" = "true" ]; then
        setup_ssh_client
    elif is_strict && [ "${has_pull}" = "true" ] && [ "${has_ssh_url}" = "true" ]; then
        error "APICURIO_GITOPS_PULL_SSH_KNOWN_HOSTS is required in strict mode when using SSH repository URLs."
    fi

    # Initialize all repositories
    for i in "${!REPO_DIRS[@]}"; do
        init_repo "${REPO_DIRS[$i]}" "${REPO_BRANCHES[$i]}" "${REPO_URLS[$i]}" "${REPO_MODES[$i]}"
    done

    # Start push mode (SSH server) if any repo uses push
    if [ "${has_push}" = "true" ]; then
        setup_ssh_server
        start_ssh_server
    fi

    # Start pull loop if any repo uses pull
    if [ "${has_pull}" = "true" ]; then
        pull_loop &
        PULL_PIDS+=($!)
        log "Pull loop started (PID: ${PULL_PIDS[0]})"
    fi

    # Wait for background processes. In mixed mode, exit when either dies
    # so the container can be restarted by the orchestrator.
    if [ "${has_push}" = "true" ] && [ "${has_pull}" = "true" ]; then
        wait -n "${SSHD_PID}" "${PULL_PIDS[0]}" || true
    elif [ "${has_push}" = "true" ]; then
        wait "${SSHD_PID}" || true
    elif [ "${has_pull}" = "true" ]; then
        wait "${PULL_PIDS[0]}" || true
    fi
}

main "$@"
