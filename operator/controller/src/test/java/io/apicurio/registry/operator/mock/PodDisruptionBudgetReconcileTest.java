package io.apicurio.registry.operator.mock;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mock-server equivalent of {@code PodDisruptionBudgetITTest}.
 *
 * <p>
 * The PDB spec (labels, selectors) is verified here. The {@code status.expectedPods} and
 * {@code status.disruptionsAllowed} fields are set asynchronously by the real Kubernetes PDB
 * controller and are not available in the mock; those assertions remain in the IT suite.
 */
@QuarkusTest
public class PodDisruptionBudgetReconcileTest extends MockServerTestBase {

    @Test
    void podDisruptionBudgetsCreated() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getSpec().getApp().setReplicas(2);
        createRegistry(registry);

        awaitDeploymentSpecReplicas(deploymentName(registry, COMPONENT_APP), 2);

        String appPdbName = registry.getMetadata().getName() + "-" + COMPONENT_APP + "-poddisruptionbudget";
        String uiPdbName = registry.getMetadata().getName() + "-" + COMPONENT_UI + "-poddisruptionbudget";

        PodDisruptionBudget appPdb = awaitResourceExists(appPdbName,
                () -> client.policy().v1().podDisruptionBudget()
                        .inNamespace(namespace).withName(appPdbName).get());
        PodDisruptionBudget uiPdb = awaitResourceExists(uiPdbName,
                () -> client.policy().v1().podDisruptionBudget()
                        .inNamespace(namespace).withName(uiPdbName).get());

        assertLabelsContains(appPdb.getMetadata().getLabels(),
                "app.kubernetes.io/component=app",
                "app.kubernetes.io/managed-by=apicurio-registry-operator",
                "app.kubernetes.io/name=apicurio-registry");
        assertLabelsContains(appPdb.getSpec().getSelector().getMatchLabels(),
                "app.kubernetes.io/component=app",
                "app.kubernetes.io/name=apicurio-registry",
                "app.kubernetes.io/instance=" + registry.getMetadata().getName());

        assertLabelsContains(uiPdb.getMetadata().getLabels(),
                "app.kubernetes.io/component=ui",
                "app.kubernetes.io/managed-by=apicurio-registry-operator",
                "app.kubernetes.io/name=apicurio-registry");
        assertLabelsContains(uiPdb.getSpec().getSelector().getMatchLabels(),
                "app.kubernetes.io/component=ui",
                "app.kubernetes.io/name=apicurio-registry",
                "app.kubernetes.io/instance=" + registry.getMetadata().getName());
    }

    private static void assertLabelsContains(Map<String, String> labels, String... expected) {
        assertThat(labels.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.toSet())).contains(expected);
    }
}
