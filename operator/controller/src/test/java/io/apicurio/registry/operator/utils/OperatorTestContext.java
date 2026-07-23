package io.apicurio.registry.operator.utils;

import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * Implemented by operator test base classes ({@code ITBase}, {@code OLMITBase}) to expose the Kubernetes
 * client and namespace to the {@link OperatorTestExtension}. This avoids reflection for accessing test
 * infrastructure and allows the extension to dump cluster diagnostics on failure.
 */
public interface OperatorTestContext {

    KubernetesClient getClient();

    String getNamespace();

    /**
     * Returns {@code true} if this test runs against an OLM-installed operator. OLM tests get additional
     * diagnostics (Subscriptions, InstallPlans, CSVs, ClusterExtensions, ClusterCatalogs).
     */
    default boolean isOLMTest() {
        return false;
    }

    /**
     * Additional namespaces whose diagnostics should be dumped when a test fails, beyond the test's
     * own namespace (e.g. the dedicated namespace of a shared operator the test depends on).
     */
    default java.util.List<String> extraDiagnosticNamespaces() {
        return java.util.List.of();
    }
}
