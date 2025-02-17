package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudgetStatus;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.stream.Collectors;

@QuarkusTest
public class PodDisruptionBudgetITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(PodDisruptionBudgetITTest.class);

    @Test
    void testPodDisruptionBudget() {
        ApicurioRegistry3 registry = ResourceFactory.deserialize(
                "/k8s/examples/simple-with-studio.apicurioregistry3.yaml", ApicurioRegistry3.class);
        registry.getSpec().getApp().setReplicas(2);
        client.resource(registry).create();

        // Wait for the deployment to exist
        checkDeploymentExists(registry, ResourceFactory.COMPONENT_APP, 2);

        // Check that the two expected PodDisruptionBudget resources were created
        PodDisruptionBudget appPDB = checkPodDisruptionBudgetExists(registry, ResourceFactory.COMPONENT_APP);
        PodDisruptionBudget uiPDB = checkPodDisruptionBudgetExists(registry, ResourceFactory.COMPONENT_UI);
        PodDisruptionBudget studioPDB = checkPodDisruptionBudgetExists(registry,
                ResourceFactory.COMPONENT_STUDIO_UI);

        // Verify the content of the app component's PDB
        assertLabelsContains(appPDB.getMetadata().getLabels(), "app.kubernetes.io/component=app",
                "app.kubernetes.io/managed-by=apicurio-registry-operator",
                "app.kubernetes.io/name=apicurio-registry");
        assertLabelsContains(appPDB.getSpec().getSelector().getMatchLabels(),
                "app.kubernetes.io/component=app", "app.kubernetes.io/name=apicurio-registry",
                "app.kubernetes.io/instance=" + registry.getMetadata().getName());
        PodDisruptionBudgetStatus appPdbStatus = appPDB.getStatus();
        Assertions.assertThat(appPdbStatus.getExpectedPods()).isEqualTo(2);
        Assertions.assertThat(appPdbStatus.getDisruptionsAllowed()).isEqualTo(1);

        // Verify the content of the ui component's PDB
        assertLabelsContains(uiPDB.getMetadata().getLabels(), "app.kubernetes.io/component=ui",
                "app.kubernetes.io/managed-by=apicurio-registry-operator",
                "app.kubernetes.io/name=apicurio-registry");
        assertLabelsContains(uiPDB.getSpec().getSelector().getMatchLabels(), "app.kubernetes.io/component=ui",
                "app.kubernetes.io/name=apicurio-registry",
                "app.kubernetes.io/instance=" + registry.getMetadata().getName());
        PodDisruptionBudgetStatus uiPdbStatus = uiPDB.getStatus();
        Assertions.assertThat(uiPdbStatus.getExpectedPods()).isEqualTo(1);
        Assertions.assertThat(uiPdbStatus.getDisruptionsAllowed()).isEqualTo(0);

        // Verify the content of the studio component's PDB
        assertLabelsContains(studioPDB.getMetadata().getLabels(), "app.kubernetes.io/component=studio-ui",
                "app.kubernetes.io/managed-by=apicurio-registry-operator",
                "app.kubernetes.io/name=apicurio-registry");
        assertLabelsContains(studioPDB.getSpec().getSelector().getMatchLabels(),
                "app.kubernetes.io/component=studio-ui", "app.kubernetes.io/name=apicurio-registry",
                "app.kubernetes.io/instance=" + registry.getMetadata().getName());
        PodDisruptionBudgetStatus studioPdbStatus = studioPDB.getStatus();
        Assertions.assertThat(studioPdbStatus.getExpectedPods()).isEqualTo(1);
        Assertions.assertThat(studioPdbStatus.getDisruptionsAllowed()).isEqualTo(0);
    }

    private void assertLabelsContains(Map<String, String> labels, String... values) {
        Assertions.assertThat(labels.entrySet().stream().map(l -> l.getKey() + "=" + l.getValue())
                .collect(Collectors.toSet())).contains(values);
    }
}
