# Operator Releasing

## OLM Channels

The operator is published to multiple OLM channels:

| Channel | Purpose | Example |
|---------|---------|---------|
| `3.x` | Rolling channel — always points to the latest release across all minors. New installations default to this channel. | `3.2.5 → 3.3.0 → 3.3.1 → 3.4.0` |
| `3.2.x` | Minor-version channel — only receives patch updates within 3.2.z. For users who want to avoid minor upgrades (which may include DB migrations). | `3.2.5 → 3.2.6 → 3.2.7` |
| `3.3.x` | Same, for 3.3.z patches. | `3.3.0 → 3.3.1 → 3.3.2` |

New minor channels (e.g. `3.4.x`) are created automatically by the release workflow.

### How Channels Are Assigned

The `ROLLING_CHANNEL` variable controls whether the bundle includes the `3.x` rolling channel:

- **Releases from `main`:** CI passes `ROLLING_CHANNEL=3.x`, so the bundle gets `channels: 3.x,<minor>.x` and `defaultChannel: 3.x`.
- **Releases from maintenance branches (e.g. `3.2.x`):** `ROLLING_CHANNEL` is not set, so the bundle only gets `channels: <minor>.x` and `defaultChannel: <minor>.x`.

This ensures that patch releases on old branches don't appear in the rolling channel.

## Release Workflows

### Releasing from `main` (e.g. 3.3.1, 3.4.0)

The release workflow (`release-operator.yaml`) handles everything automatically:

1. Builds the bundle with `ROLLING_CHANNEL=3.x` → `channels: 3.x,3.3.x`
2. Builds and pushes the bundle and catalog images
3. Updates the catalog template (`catalog.template.yaml`):
   - Adds the released version to the `3.x` rolling channel
   - Adds the released version to the minor channel (e.g. `3.3.x`)
   - If this is a new minor (e.g. 3.4.0 after 3.3.x), creates the new `3.4.x` channel automatically
4. Submits the bundle to community-operators repos

No manual intervention required.

### Releasing from a maintenance branch (e.g. 3.2.6 from `3.2.x`)

1. Builds the bundle without `ROLLING_CHANNEL` → `channels: 3.2.x`
2. The bundle only appears in the `3.2.x` channel
3. Users on the `3.x` rolling channel do not see this release
4. Post-release changes are synced back to `main`

No manual intervention required.

### Cross-minor releases (e.g. 3.4.0 after 3.3.x)

When the release version's minor differs from the previous version's minor (e.g. releasing 3.4.0 when `PREVIOUS_PACKAGE_VERSION` is 3.3.x):

1. The old minor channel (`3.3.x`) is finalized — the released version replaces the placeholder
2. A new minor channel (`3.4.x`) is created with a placeholder entry
3. The `3.x` rolling channel gets the new version

The `release-catalog-template-update` make target handles this automatically.

## Community Operators

After each release, the bundle is submitted to:

- **[community-operators-prod](https://github.com/redhat-openshift-ecosystem/community-operators-prod)** (OpenShift OperatorHub)
- **[community-operators](https://github.com/k8s-operatorhub/community-operators)** (OperatorHub.io)

The release workflow creates these PRs automatically. Key points:

- community-operators-prod uses File-Based Catalog (FBC) — channel membership can be updated retroactively
- community-operators (k8s) uses legacy replaces-mode — bundles are immutable once merged
- `ci.yaml` configuration differs between the two repos

## Precedent

OpenShift GitOps uses the same multi-channel pattern: `latest,gitops-1.16` (rolling + version-pinned).
