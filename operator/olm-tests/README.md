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
- **`ChannelValidationOLMITTest`** and **`UpgradeOLMITTest`** install released bundles from the catalog
  to exercise channel and upgrade behavior.
