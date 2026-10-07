# Operator OLM tests

Integration tests that install the operator through the Operator Lifecycle Manager and exercise it on
a real cluster. The same tests run against both OLM v0 (OLM classic, `operator-framework/operator-lifecycle-manager`)
and OLM v1 (`operator-framework/operator-controller`), selected by the `test.operator.olm-version`
system property (`0` is the default, `1` is set by the olm-v1 CI job).

Most of the setup complexity here comes from how each OLM version scopes the operator's RBAC, and one
subtlety in OLM v1 that a real deployer is most likely to trip on. This document explains it and how
the tests model it.

## OLM v0 vs v1: how `permissions` is scoped

The CSV declares two permission sets: `permissions` (namespace-scoped workloads) and `clusterPermissions`
(cluster-scoped CR/CRD discovery). What happens to `permissions` at install time is the detail that
matters.

**OLM v0** uses an `OperatorGroup` to scope `permissions` based on the install mode:

| Install mode                 | What OLM v0 does with `permissions`                          |
| ---------------------------- | ------------------------------------------------------------ |
| SingleNamespace / OwnNamespace | Creates a Role + RoleBinding in the target namespace       |
| MultiNamespace               | Copies the Role + RoleBinding into each listed namespace     |
| AllNamespaces (`*`)          | Promotes each rule to a ClusterRole + ClusterRoleBinding     |

**OLM v1** dropped the user-facing `OperatorGroup` (a `ClusterExtension` has a single `namespace` field
instead), but the bundle renderer kept v0's promotion mechanic. In AllNamespaces mode it promotes each
`permissions` rule to a ClusterRole + ClusterRoleBinding and appends `namespaces get/list/watch`,
mirroring v0's `operatorgroup.go`; in single-namespace mode it would create a namespaced Role instead.
This bundle currently always resolves to **AllNamespaces**: selecting single/own-namespace under v1
needs the `SingleOwnNamespaceInstallSupport` feature gate, which is alpha and off by default. So under
v1 today the operator receives its workload verbs cluster-wide via promotion, automatically, the same
effective scope as v0 AllNamespaces. No manual admin grant is involved.

## What each deployment scenario needs

The operator watches all namespaces by default (`apicurio.operator.watched-namespaces` unset). Per OLM
version:

- **Single-namespace** (operator configured to watch only its own namespace): the namespaced Role from
  `permissions` is enough. On v0 this is a standard OwnNamespace/SingleNamespace install; on v1 it
  requires the alpha single-namespace feature gate above, so it is not yet a supported path here.
- **Multi-namespace** (a specific list): on v0 the `OperatorGroup` replicates the Role into each target
  namespace; not available on v1 without the same alpha gate.
- **All-namespace**: on both versions `permissions` is promoted to a ClusterRole + ClusterRoleBinding,
  so the operator gets its workload verbs cluster-wide and its informers can list across the cluster. On
  v1 the promotion additionally requires the installer ClusterRole to hold `namespaces get/list/watch`
  (see below); without it the promoted ClusterRole is rejected by escalation prevention, the informers
  403 at the cluster scope, and the operator CrashLoopBackOffs.

## The installer ClusterRole (OLM v1)

`src/test/deploy/olmv1/cluster-role.yaml` is the ClusterRole bound to the **installer** ServiceAccount
(`apicurio-registry-operator-installer`), the one OLM v1 uses to apply the bundle. OLM v1 enforces RBAC
escalation prevention, so the installer can only grant permissions it already holds. This role must
therefore be a **superset** of everything the operator could be granted at runtime, including the
SDK-generated `namespaces get/list/watch` that a watch-all-namespaces operator needs. It is not the
operator's runtime RBAC, it is the set the installer is allowed to hand out.

`RbacInstallerSyncTest` (in the controller module) guards this superset relationship against the
operator's declared RBAC in `controller/src/main/deploy/rbac/namespaced`, plus the
`namespaces get/list/watch` rule that OLM v1 injects via AllNamespaces promotion (which is not declared
there). Both the declared workload verbs and the promotion-injected `namespaces` rule are therefore
checked without a cluster; the live OLM v1 smoke run is the end-to-end backstop, not the only guard.

## How the tests model each scenario

- **`SmokeOLMITTest`** installs this repo's freshly built bundle and waits for the operator to become
  ready. It needs no extra RBAC setup on either OLM version: v0's `OperatorGroup` and v1's AllNamespaces
  promotion each grant the operator its workload verbs cluster-wide, so a green v1 run genuinely
  exercises the promotion path rather than a manually applied grant standing in for it.
- **`NamespacedPermissionsOLMITTest`** and **`AllNamespacesOLMITTest`** assert the v0 least-privilege
  boundary directly with `SubjectAccessReview`s, and are disabled under v1: they rely on `OperatorGroup`
  install modes and the SingleNamespace boundary, neither of which v1 exposes today.
- **`ChannelValidationOLMITTest`** validates channel metadata (names, heads, default channel) against
  the live catalog.
- **`UpgradeOLMITTest`** (OLM v0 only) installs an older version from the catalog and verifies OLM upgrades
  it to the current version, driven by a `Subscription`/`InstallPlan`. It discovers available versions from
  the live catalog at runtime rather than hardcoding version strings, so it works with both upstream
  catalogs (built from `catalog.template.yaml`) and downstream IIB images (where CSV names have
  productized suffixes like `-r1`).
- **`UpgradeOLMv1ITTest`** (OLM v1 only) is the `ClusterExtension` counterpart of `UpgradeOLMITTest`,
  covering the same scenarios except manual-approval upgrade (see below). OLM v1 has no `Subscription`
  auto-resolve-to-newest-in-channel behavior, so "upgrading" means patching
  `spec.source.catalog.version`/`channels` on the `ClusterExtension` directly and waiting for the operator
  deployment to follow. Catalog discovery (channels, versions, heads) is shared with `UpgradeOLMITTest` via
  `CatalogDiscovery`, which under OLM v1 reads File-Based Catalog content from catalogd (via
  `CatalogdClient`, extracted from `ChannelValidationOLMITTest`) instead of exec'ing into the catalog pod,
  and feeds it through the same FBC parser used by OLM v0.

## OLM upgrade test scenarios

The upgrade tests exercise OLM's version resolution across channels. Which tests are applicable depends
on which channels the current version appears in, which in turn depends on the release branch.

### Channel model

- **Rolling channel (`3.x`):** receives every new minor release from `main`. Contains the full version
  history across minors (3.0.x → 3.1.x → 3.2.x → 3.3.x → ...).
- **Minor channels (`3.2.x`, `3.3.x`, ...):** receive only patch releases for that minor. A version
  released from a maintenance branch (e.g., 3.2.7 from the `3.2.x` branch) goes into `3.2.x` only, not
  into `3.x`.

### Release from `main` (e.g., 3.3.1)

The version goes into both `3.x` (rolling) and `3.3.x` (minor). All upgrade tests are applicable:

| Test | Channel | Start → Target | What it verifies |
|------|---------|----------------|------------------|
| Upgrade within minor | `3.3.x` | 3.3.0 → 3.3.1 | Patch upgrade within the current minor channel |
| Upgrade across minors | `3.x` | previous minor → 3.3.1 | Cross-minor upgrade via the rolling channel |
| Channel switch rolling→minor | `3.x` → `3.3.x` | 3.3.0 → 3.3.1 | Install on rolling, switch to minor |
| Minor channel isolation | `3.3.x` | 3.3.0 → 3.3.1 | Verify no leak to a different minor |
| Fresh install on minor | `3.3.x` | (none) → 3.3.1 | Fresh install gets the channel head |
| Manual approval upgrade | `3.3.x` | 3.3.0 → 3.3.1 | Upgrade with manual install plan approval |
| Downgrade rejected | `3.x` → older minor | 3.3.1 stays | Channel switch doesn't downgrade |
| Channel switch noop | `3.x` → `3.3.x` | 3.3.1 stays | Switch at head is a no-op |

### First release of a new minor from `main` (e.g., 3.4.0)

The version goes into `3.x` and `3.4.x`. The minor channel has only one entry, so within-minor upgrade
tests are not applicable:

| Test | Applicable? | Reason |
|------|-------------|--------|
| Upgrade within minor | No | `3.4.x` has only one entry (3.4.0) |
| Upgrade across minors | Yes | `3.x` has entries from previous minors |
| Channel switch rolling→minor | Yes | Version is in both channels |
| Minor channel isolation | No | Nothing to upgrade from in `3.4.x` |
| Fresh install on minor | Yes | Always applicable |
| Manual approval upgrade | No | `3.4.x` has only one entry |
| Downgrade rejected | Yes | Version is in `3.x` |
| Channel switch noop | Yes | Version is in both channels |

### Release from a maintenance branch (e.g., 3.2.7 from `3.2.x`)

The version goes into `3.2.x` only, not `3.x`. Tests involving the rolling channel are not applicable:

| Test | Applicable? | Reason |
|------|-------------|--------|
| Upgrade within minor | Yes | `3.2.x` has previous entries |
| Upgrade across minors | No | Version is not in `3.x` |
| Channel switch rolling→minor | No | Version is not in `3.x` |
| Minor channel isolation | Yes | Verify no leak beyond `3.2.x` |
| Fresh install on minor | Yes | Always applicable |
| Manual approval upgrade | Yes | `3.2.x` has previous entries |
| Downgrade rejected | No | Version is not in `3.x` |
| Channel switch noop | No | Version is not in `3.x` |

### OLM v1 coverage and its limits

`UpgradeOLMv1ITTest` covers upgrade within a minor channel, cross-minor upgrade, channel switch
(rolling→minor, and the noop case at channel head), minor channel isolation, fresh install on a minor
channel, and rejected downgrade — the same scenarios `UpgradeOLMITTest` covers, minus manual-approval
upgrade. OLM v1's `ClusterExtension` has no `InstallPlan`/approval concept: resolution is either automatic
(whatever version satisfies the constraint at reconcile time) or explicit user-driven (edit the constraint
yourself), with no intermediate "pending approval" object to approve. `testManualApprovalUpgrade` therefore
stays OLM v0-only, gated off entirely for v1 the same way the rest of `UpgradeOLMITTest` already is.

For "fresh install on a minor channel," `UpgradeOLMv1ITTest` deploys a channel-only `ClusterExtension`
with no `spec.source.catalog.version` — the same shape as OLM v0's `startingCSV`-less Subscription — and
asserts that OLM v1 resolves it to the discovered channel head. The resolved version is read back from
the installed operator deployment, so the test exercises OLM v1's default channel-head resolution rather
than re-checking a version it pinned itself.

The cross-minor upgrade test only runs when the rolling channel's cross-minor entry is at least
`3.3.2`, the first release whose bundle carries the cluster-tier CSV RBAC an OLM v1 `ClusterExtension`
install needs. Earlier bundles rely on an OLM v0 `OperatorGroup` to scope the operator's watch, which
`ClusterExtension` has no equivalent for, so upgrades starting from them stay covered by
`UpgradeOLMITTest` (OLM v0) only.

### How tests determine applicability

Tests query the live catalog after the `CatalogSource`/`ClusterCatalog` is created: OLM v0 reads FBC
content by exec'ing into the catalog pod, OLM v1 reads the same FBC format from catalogd over HTTP. Both
paths are parsed by the same `CatalogDiscovery.parseFBC` method into a shared `CatalogInfo` model, and the
discovery result is cached for the duration of the test run. Each test checks its preconditions against
that model and logs the reason when skipping:

- **Is the current version in `3.x`?** Check if `3.x` channel entries contain a CSV matching the current
  package version.
- **Does the minor channel have ≥2 entries?** Required for any within-minor upgrade test.
- **Does `3.x` have entries from a different minor?** Required for cross-minor upgrade tests.

This approach works for both upstream catalogs and downstream IIB images, since it reads the actual
catalog content rather than assuming a specific structure.
