package io.apicurio.registry.operator.mock;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mock-server equivalent of {@code NetworkPolicyITTest}.
 */
@QuarkusTest
public class NetworkPolicyReconcileTest extends MockServerTestBase {

    @Test
    void networkPoliciesCreated() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        createRegistry(registry);

        awaitDeploymentExists(deploymentName(registry, COMPONENT_APP));

        String appPolicyName = registry.getMetadata().getName() + "-" + COMPONENT_APP + "-networkpolicy";
        String uiPolicyName = registry.getMetadata().getName() + "-" + COMPONENT_UI + "-networkpolicy";

        NetworkPolicy appPolicy = awaitResourceExists(appPolicyName,
                () -> client.network().v1().networkPolicies().inNamespace(namespace).withName(appPolicyName).get());
        NetworkPolicy uiPolicy = awaitResourceExists(uiPolicyName,
                () -> client.network().v1().networkPolicies().inNamespace(namespace).withName(uiPolicyName).get());

        assertLabelsContains(appPolicy.getMetadata().getLabels(),
                "app.kubernetes.io/component=app",
                "app.kubernetes.io/managed-by=apicurio-registry-operator",
                "app.kubernetes.io/name=apicurio-registry");
        assertLabelsContains(appPolicy.getSpec().getPodSelector().getMatchLabels(),
                "app.kubernetes.io/component=app",
                "app.kubernetes.io/name=apicurio-registry",
                "app.kubernetes.io/instance=" + registry.getMetadata().getName());

        assertLabelsContains(uiPolicy.getMetadata().getLabels(),
                "app.kubernetes.io/component=ui",
                "app.kubernetes.io/managed-by=apicurio-registry-operator",
                "app.kubernetes.io/name=apicurio-registry");
        assertLabelsContains(uiPolicy.getSpec().getPodSelector().getMatchLabels(),
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
