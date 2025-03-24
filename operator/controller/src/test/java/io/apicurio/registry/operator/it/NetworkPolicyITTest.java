package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.stream.Collectors;

@QuarkusTest
public class NetworkPolicyITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(NetworkPolicyITTest.class);

    @Test
    void testNetworkPolicy() {
        ApicurioRegistry3 registry = ResourceFactory.deserialize(
                "/k8s/examples/simple.apicurioregistry3.yaml", ApicurioRegistry3.class);
        client.resource(registry).create();

        // Wait for the deployment to exist
        checkDeploymentExists(registry, ResourceFactory.COMPONENT_APP, 1);

        // Check that the two expected NetworkPolicy resources were created
        NetworkPolicy appPolicy = checkNetworkPolicyExists(registry, ResourceFactory.COMPONENT_APP);
        NetworkPolicy uiNetworkPolicy = checkNetworkPolicyExists(registry, ResourceFactory.COMPONENT_UI);

        // Verify the content of the app component's network policy
        assertLabelsContains(appPolicy.getMetadata().getLabels(), "app.kubernetes.io/component=app",
                "app.kubernetes.io/managed-by=apicurio-registry-operator",
                "app.kubernetes.io/name=apicurio-registry");
        assertLabelsContains(appPolicy.getSpec().getPodSelector().getMatchLabels(),
                "app.kubernetes.io/component=app", "app.kubernetes.io/name=apicurio-registry",
                "app.kubernetes.io/instance=" + registry.getMetadata().getName());

        // Verify the content of the ui component's network policy
        assertLabelsContains(uiNetworkPolicy.getMetadata().getLabels(), "app.kubernetes.io/component=ui",
                "app.kubernetes.io/managed-by=apicurio-registry-operator",
                "app.kubernetes.io/name=apicurio-registry");
        assertLabelsContains(uiNetworkPolicy.getSpec().getPodSelector().getMatchLabels(),
                "app.kubernetes.io/component=ui", "app.kubernetes.io/name=apicurio-registry",
                "app.kubernetes.io/instance=" + registry.getMetadata().getName());
    }

    private void assertLabelsContains(Map<String, String> labels, String... values) {
        Assertions.assertThat(labels.entrySet().stream().map(l -> l.getKey() + "=" + l.getValue())
                .collect(Collectors.toSet())).contains(values);
    }
}