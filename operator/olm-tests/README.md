# Operator OLM tests

Integration tests that install the operator through the Operator Lifecycle Manager and exercise it on
a real cluster. The same tests run against both OLM v0 (OLM classic, `operator-framework/operator-lifecycle-manager`)
and OLM v1 (`operator-framework/operator-controller`), selected by the `test.operator.olm-version`
system property (`0` is the default, `1` is set by the olm-v1 CI job).

Most of the setup complexity here comes from one difference between the two OLM versions: **who owns
the operator's RBAC scope**. This document explains that difference and how the tests model it, since
it is the thing a real deployer is most likely to trip on.

## OLM v0 vs v1: who manages RBAC scope

The CSV declares two permission sets: `permissions` (intended namespace-scoped) and `clusterPermissions`
(cluster-scoped). What happens to `permissions` is where the two OLM versions diverge.

**OLM v0** uses an `OperatorGroup` to manage scope on the operator's behalf, based on the install mode:

| Install mode                 | What OLM v0 does with `permissions`                          |
| ---------------------------- | ------------------------------------------------------------ |
| SingleNamespace / OwnNamespace | Creates a Role + RoleBinding in the target namespace       |
| MultiNamespace               | Copies the Role + RoleBinding into each listed namespace     |
| AllNamespaces (`*`)          | Promotes each rule to a ClusterRole + ClusterRoleBinding     |

**OLM v1** deliberately dropped that abstraction. A `ClusterExtension` has a single `namespace` field,
and OLM v1 creates the Role from `permissions` in that one namespace, period. There is no
`OperatorGroup`, no promotion, and no multi-namespace replication. Anything beyond single-namespace
access is the cluster admin's responsibility to grant explicitly.

## What each deployment scenario needs

The operator watches all namespaces by default (`apicurio.operator.watched-namespaces` unset). What
that costs, per OLM version:

- **Single-namespace** (operator configured to watch only its own namespace): works out of the box on
  both versions. The namespaced Role from `permissions` is enough.
- **Multi-namespace** (a specific list): on v0 the `OperatorGroup` replicates the Role into each target
  namespace; on v1 the admin creates a Role + RoleBinding (or a ClusterRole) in each extra namespace.
- **All-namespace**: on v0 the `AllNamespaces` `OperatorGroup` promotes `permissions` to a ClusterRole;
  on v1 the admin creates a ClusterRole + ClusterRoleBinding granting the operator ServiceAccount
  cluster-wide workload access. Without it the operator's informers issue cluster-scoped list calls,
  get a 403, and CrashLoopBackOff.

## The installer ClusterRole (OLM v1)

`src/test/deploy/olmv1/cluster-role.yaml` is the ClusterRole bound to the **installer** ServiceAccount
(`apicurio-registry-operator-installer`), the one OLM v1 uses to apply the bundle. OLM v1 enforces RBAC
escalation prevention, so the installer can only grant permissions it already holds. This role must
therefore be a **superset** of everything the operator could be granted at runtime, including the
SDK-generated `namespaces get/list/watch` that a watch-all-namespaces operator needs. It is not the
operator's runtime RBAC, it is the set the installer is allowed to hand out.

`RbacInstallerSyncTest` (in the controller module) guards this superset relationship against the
operator's *declared* RBAC in `controller/src/main/deploy/rbac/namespaced`. Permissions that are
generated at build time rather than declared there (such as `namespaces`) are not covered by that
guard, so keep the installer role's comments in sync when they change.

## How the tests model each scenario

- **`SmokeOLMITTest`** installs this repo's freshly built bundle and waits for the operator to become
  ready. Under OLM v1 it applies `olmv1/operator-workload-clusterrole.yaml` and its binding in
  `@BeforeAll` to model the all-namespace admin step above, and removes them in `@AfterAll` so the
  cluster-scoped grant cannot leak into later tests. Under v0 no extra setup is needed.
- **`NamespacedPermissionsOLMITTest`** and **`AllNamespacesOLMITTest`** assert the v0 least-privilege
  boundary directly with `SubjectAccessReview`s, and are disabled under v1 (the v0 promotion behavior
  they check does not exist there).
- **`ChannelValidationOLMITTest`** and **`UpgradeOLMITTest`** install released bundles from the catalog
  to exercise channel and upgrade behavior, so they do not need the admin-step grant.
