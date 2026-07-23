package io.apicurio.registry.operator.utils;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.List;

/**
 * Implemented by operator test base classes ({@code ITBase}, {@code OLMITBase}) to expose the Kubernetes
 * client and namespace to the {@link OperatorTestExtension}. This avoids reflection for accessing test
 * infrastructure and allows the extension to dump cluster diagnostics on failure.
 */
public interface OperatorTestContext {

    ExtensionContext.Namespace STORE_NAMESPACE = ExtensionContext.Namespace.create(OperatorTestContext.class);

    KubernetesClient getClient();

    String getNamespace();

    /**
     * Returns {@code true} if this test runs against an OLM-installed operator. OLM tests get additional
     * diagnostics (Subscriptions, InstallPlans, CSVs, ClusterExtensions, ClusterCatalogs).
     */
    default boolean isOLMTest() {
        return false;
    }

    default List<String> extraDiagnosticNamespaces() {
        return List.of();
    }
}
