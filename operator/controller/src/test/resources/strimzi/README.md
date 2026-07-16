# Vendored Strimzi cluster-operator manifest

`strimzi-cluster-operator-0.47.0.yaml` is the unmodified release manifest from
https://github.com/strimzi/strimzi-kafka-operator/releases/download/0.47.0/strimzi-cluster-operator-0.47.0.yaml

It is vendored so integration tests do not download it from GitHub at runtime (network flakiness,
supply-chain pinning). All transformations for cluster-wide watch (STRIMZI_NAMESPACE=*, RoleBinding
to ClusterRoleBinding conversion) are applied in code by `StrimziClusterWideInstaller`, NOT edited
into this file — keep it byte-identical to upstream.

To bump the Strimzi version:
1. Download the new release manifest into this directory (keep the versioned filename).
2. Update `StrimziClusterWideInstaller.MANIFEST_RESOURCE` and delete the old file.
3. Check the Strimzi docs "watch all namespaces" section for RBAC changes (unexpected RoleBinding names fail fast at install time via CONVERTIBLE_ROLE_BINDINGS in
   StrimziClusterWideInstaller, and StrimziClusterWideInstallerTest asserts the full transform).
4. Note: Strimzi 0.48+ removed support for Kafka 3.9.x — coordinate with the Kafka CR versions
   in `src/test/resources/k8s/examples/kafkasql/`.
