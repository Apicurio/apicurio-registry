package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

@QuarkusTest
public class PodDisruptionBudgetITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(PodDisruptionBudgetITTest.class);

    @Test
    void testPodDisruptionBudget() {
        ApicurioRegistry3 registry = ResourceFactory
                .deserialize("/k8s/examples/simple.apicurioregistry3.yaml", ApicurioRegistry3.class);
        client.resource(registry).create();

        // Wait for the deployment to exist
        checkDeploymentExists(registry, ResourceFactory.COMPONENT_APP, 1);

        // Check that the two expected PodDisruptionBudget resources were created
        checkPodDisruptionBudgetExists(registry, ResourceFactory.COMPONENT_APP);
        checkPodDisruptionBudgetExists(registry, ResourceFactory.COMPONENT_UI);

        // Verify the content of the app component's PDB
        PodDisruptionBudget appPDB = client.policy().v1().podDisruptionBudget()
                .withName(registry.getMetadata().getName() + "-app-podDisruptionBudget").get();
        Assertions
                .assertThat(appPDB.getMetadata().getLabels().entrySet().stream()
                        .map(l -> l.getKey() + "=" + l.getValue()).collect(Collectors.toSet()))
                .contains("app.kubernetes.io/component=app",
                        "app.kubernetes.io/managed-by=apicurio-registry-operator",
                        "app.kubernetes.io/name=apicurio-registry");
        Assertions
                .assertThat(appPDB.getSpec().getSelector().getMatchLabels().entrySet().stream()
                        .map(l -> l.getKey() + "=" + l.getValue()).collect(Collectors.toSet()))
                .contains("app.kubernetes.io/component=app", "app.kubernetes.io/name=apicurio-registry");

        // Verify the content of the ui component's PDB
        PodDisruptionBudget uiPDB = client.policy().v1().podDisruptionBudget()
                .withName(registry.getMetadata().getName() + "-ui-podDisruptionBudget").get();
        Assertions
                .assertThat(uiPDB.getMetadata().getLabels().entrySet().stream()
                        .map(l -> l.getKey() + "=" + l.getValue()).collect(Collectors.toSet()))
                .contains("app.kubernetes.io/component=ui",
                        "app.kubernetes.io/managed-by=apicurio-registry-operator",
                        "app.kubernetes.io/name=apicurio-registry");
        Assertions
                .assertThat(uiPDB.getSpec().getSelector().getMatchLabels().entrySet().stream()
                        .map(l -> l.getKey() + "=" + l.getValue()).collect(Collectors.toSet()))
                .contains("app.kubernetes.io/component=ui", "app.kubernetes.io/name=apicurio-registry");
    }
}
