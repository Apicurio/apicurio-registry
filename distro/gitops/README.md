# Apicurio Registry GitOps Sync

**Image:** `quay.io/apicurio/apicurio-registry-gitops-sync`

A lightweight Git synchronization container for the Apicurio Registry GitOps storage mode.
It manages a local Git repository on a shared volume that the registry reads from.

## Features

- **Pull mode** (default) — periodically fetches from a remote Git repository, keeping
  the local clone up to date. The registry detects changes and reloads automatically.
- **Push mode** — runs an SSH server that accepts `git push` from CI/CD pipelines
  or developers, making updates available to the registry on the next poll cycle.
- **SSH authentication** — supports SSH keys for pull mode and authorized_keys for push mode.
- **Shallow clones** — uses `--depth 1` by default to minimize bandwidth and disk usage.
- **Graceful shutdown** — handles SIGTERM/SIGINT for clean container orchestration.

## Quick Start

See [`examples/gitops/`](../../examples/gitops/) for ready-to-run Docker Compose examples
covering local volume, HTTPS pull, and SSH pull setups.

## Configuration Reference

All environment variables use the `APICURIO_GITOPS_` prefix. Variables that correspond to
registry configuration properties share the same name, so the sidecar and registry can be
configured consistently.

### Single-Repo (Shared with Registry)

These variables are understood by both the sidecar and the registry container.
Use these for simple single-repo setups:

| Variable | Default | Description |
|----------|---------|-------------|
| `APICURIO_GITOPS_WORKSPACE` | `/repos` | Base directory where repositories are stored |
| `APICURIO_GITOPS_REPO_DIR` | `default` | Repository directory name, relative to workspace |
| `APICURIO_GITOPS_REPO_BRANCH` | `main` | Git branch to track |
| `APICURIO_GITOPS_REPO_URL` | *(required for pull)* | Remote repository URL (sidecar only) |

### Multi-Repo (Indexed)

For multiple repositories, use indexed variables. The same format is used by
both the sidecar and the registry:

| Variable | Default | Description |
|----------|---------|-------------|
| `APICURIO_GITOPS_REPOS_N_DIR` | *(required)* | Directory name for repo N |
| `APICURIO_GITOPS_REPOS_N_BRANCH` | `main` | Branch to track for repo N |
| `APICURIO_GITOPS_REPOS_N_URL` | *(required for pull)* | Remote URL for repo N |
| `APICURIO_GITOPS_REPOS_N_SSH_KEYS` | *(none)* | SSH keys for repo N (added alongside global `APICURIO_GITOPS_PULL_SSH_KEYS`) |
| `APICURIO_GITOPS_REPOS_N_MODE` | *(global `APICURIO_GITOPS_MODE`)* | Mode for repo N: `pull` or `push` (overrides global) |

Indexes must be dense (0, 1, 2, ... — no gaps). If indexed repos are configured,
single-repo shorthand variables must not be set.

**Registry equivalent** (in `application.properties` or env vars):
```properties
apicurio.gitops.repos.0.dir=platform
apicurio.gitops.repos.1.dir=fulfillment
apicurio.gitops.repos.1.branch=fulfillment
```

### Sidecar-only

| Variable | Default | Description |
|----------|---------|-------------|
| `APICURIO_GITOPS_MODE` | `pull` | Operating mode: `pull` or `push` |
| `APICURIO_GITOPS_PULL_INTERVAL` | `30` | Seconds between fetch attempts (pull mode) |
| `APICURIO_GITOPS_PULL_DEPTH` | `1` | Git clone/fetch depth. `0` for full history (pull mode) |
| `APICURIO_GITOPS_PULL_SSH_KEYS` | *(none)* | Path to SSH private key for pull authentication. Comma-separated list for multiple keys (SSH tries each in order). |
| `APICURIO_GITOPS_PULL_SSH_KNOWN_HOSTS` | *(none)* | Path to SSH `known_hosts` file (pull mode) |
| `APICURIO_GITOPS_PUSH_PORT` | `2222` | SSH server listen port (push mode) |
| `APICURIO_GITOPS_PUSH_SSH_AUTHORIZED_KEYS` | *(none)* | Path to `authorized_keys` for push access |
| `APICURIO_GITOPS_PUSH_SSH_HOST_KEY` | *(none)* | Path to SSH host private key for push server |
| `APICURIO_GITOPS_SECURITY` | `strict` | Security level: `strict` or `dev` (see below) |

## Security Levels

The `APICURIO_GITOPS_SECURITY` variable controls how strictly the sidecar enforces
security requirements. Two levels are available:

- **`strict`** (default) — all credentials must be explicitly provided. Fails fast on any
  missing or misconfigured security material.
- **`dev`** — auto-generates SSH host keys, uses TOFU for host verification, warns on
  issues instead of failing. Use for local development and testing.

| Feature | `dev` | `strict` (default) |
|---------|-------|--------------------|
| SSH known_hosts (pull) | TOFU (`accept-new`) | **Required** — fails if not provided |
| SSH host key (push) | Auto-generated | **Required** — fails if not mounted |
| SSH authorized_keys (push) | Warns if missing | **Required** — fails if not provided |
| SSH private key permissions | Warns on loose permissions | **Fails** on loose permissions |
| Repository URL scheme | **Rejects plaintext HTTP** | **Rejects plaintext HTTP** |

## How It Works

### Pull mode

1. On startup, the sidecar clones the repository (shallow by default) into `WORKSPACE/REPO_DIR`.
2. A background loop runs `git fetch` + `git reset --hard origin/BRANCH` every `PULL_INTERVAL` seconds.
3. The registry container mounts the same volume read-only and polls for new commits using JGit.
4. When a new commit is detected, the registry loads data into its inactive database and performs
   an atomic swap (blue-green loading).

### Push mode

1. On startup, the sidecar initializes a non-bare repository configured with
   `receive.denyCurrentBranch=updateInstead`, allowing pushes to update the working tree.
2. An SSH server starts on `PUSH_PORT`, restricted to a `git` user with `git-shell`.
3. External clients push changes via SSH; the working tree updates in place.
4. The registry detects the new commit on its next poll cycle.

### Mixed mode

In multi-repo setups, each repo can use a different mode via `APICURIO_GITOPS_REPOS_N_MODE`.
For example, one repo can pull from a remote while another accepts pushes. The sidecar runs
both the pull loop and SSH server simultaneously when needed. Push-mode repos reject pulls
(no remote URL), and pull-mode repos reject pushes (git denies updates to checked-out branches
without `updateInstead`).

## Security

### Threat Model

The sidecar handles Git credentials and exposes an SSH server (in push mode), making it a
potential target. The following analysis covers the main risks and mitigations.

### Container Isolation

| Threat | Mitigation |
|--------|------------|
| Container escape via sidecar compromise | Runs as non-root `git` user (UID dynamically assigned). No elevated capabilities required. |
| Privilege escalation inside container | The `git` user's login shell is `git-shell`, which only allows git commands. No general shell access. |
| Lateral movement to registry | The shared volume is mounted read-only in the registry container. A compromised sidecar cannot inject code into the registry process. |

### SSH Credentials (Pull Mode)

| Threat | Mitigation |
|--------|------------|
| Key exposure via mounted volume | The sidecar copies SSH keys to `~/.ssh/id_key_N` with `0600` permissions. The original mount is not modified. |
| Man-in-the-middle on SSH connections | When `APICURIO_GITOPS_PULL_SSH_KNOWN_HOSTS` is provided, `StrictHostKeyChecking=yes` is enforced. Without it, `accept-new` is used (TOFU). |
| Key leakage in process listing | The key path is passed via environment variable and used in an SSH config file, not on the command line. |
| Accidental use of wrong key | `IdentitiesOnly=yes` is set, preventing SSH from trying keys from an agent. |

**Recommendation:** Always provide a `known_hosts` file in production to prevent TOFU-based
MITM attacks. Generate it with `ssh-keyscan github.com > known_hosts`.

### SSH Server (Push Mode)

| Threat | Mitigation |
|--------|------------|
| Unauthorized access | Password and keyboard-interactive authentication are disabled. Only public key authentication is allowed. |
| Shell escape / command injection | The `git` user's login shell is `git-shell`, which only accepts `git-receive-pack`, `git-upload-pack`, and `git-upload-archive`. Arbitrary commands are rejected. |
| Port scanning / brute force | `MaxAuthTries=3`, `LoginGraceTime=30s`, and `MaxStartups=5:50:10` provide rate limiting and connection throttling. |
| Network-level attacks | `AllowTcpForwarding=no`, `AllowAgentForwarding=no`, `PermitTunnel=no`, `GatewayPorts=no`, and `X11Forwarding=no` disable all forwarding capabilities. |
| Host key instability across restarts | Mount a persistent host key via `APICURIO_GITOPS_PUSH_SSH_HOST_KEY` (required in strict mode). In dev mode, keys are auto-generated. |
| Weak host keys | ED25519 (primary) and RSA-4096 (fallback) host keys are generated. No DSA or small RSA keys. |
| Root login | `PermitRootLogin=no` and `AllowUsers=git` restrict access to the dedicated `git` user. |

**Recommendation:** In Kubernetes, use a `NetworkPolicy` to restrict which pods can reach
the SSH port. Do not expose the SSH port outside the cluster unless necessary.

### Git Repository Integrity

| Threat | Mitigation |
|--------|------------|
| Malicious content in repository | The registry validates all data during loading. Invalid files cause a load failure, and the registry continues serving the last known good data (blue-green swap is not performed). |
| Force-push erasing history | Shallow clones (`--depth 1`) limit exposure. The registry reads from pinned commit SHAs, so concurrent force-pushes do not corrupt in-flight reads. |
| Symlink attacks in repository | JGit (used by the registry) does not follow symlinks when reading Git objects. The registry reads from the Git object store, not the working tree. |

### Recommendations for Production Deployments

1. **Use read-only volume mounts** — mount the shared volume as `:ro` in the registry container.
2. **Provide known_hosts** — avoid TOFU by pre-populating the known_hosts file.
3. **Use Kubernetes Secrets** — mount SSH keys as Kubernetes Secrets, not ConfigMaps.
4. **Restrict network access** — use NetworkPolicy to limit SSH server exposure (push mode).
5. **Run with minimal capabilities** — no additional Linux capabilities are needed. Consider
   adding `securityContext.readOnlyRootFilesystem: true` with writable `emptyDir` mounts for
   `/home/git`, `/repos`, and `/tmp`.
6. **Monitor sidecar logs** — the sidecar logs all fetch attempts, SSH connections, and errors
   to stdout in a structured timestamp format.

## Building the Image

The image is built directly with `docker build` — no Maven plugin is involved.

```bash
# Build locally
docker build -t quay.io/apicurio/apicurio-registry-gitops-sync:latest distro/gitops

# Build multi-architecture (for CI/release)
docker buildx build --push \
  -t quay.io/apicurio/apicurio-registry-gitops-sync:latest \
  --platform linux/amd64,linux/arm64,linux/s390x,linux/ppc64le \
  distro/gitops
```

The build context is self-contained — it only needs the files in `distro/gitops/` (Dockerfile,
entrypoint script, and config templates). No Maven build step is required.

## Related Documentation

- [GitOps Storage Overview](../../app/src/main/java/io/apicurio/registry/storage/impl/gitops/README.md) — architecture, configuration, management API, data format, error handling
- [Docker Compose Examples](../../examples/gitops/) — ready-to-run examples for local volume, HTTPS pull, and SSH pull
- [GitOps Design Epic](https://github.com/Apicurio/apicurio-registry/issues/7480) — design document and implementation plan
