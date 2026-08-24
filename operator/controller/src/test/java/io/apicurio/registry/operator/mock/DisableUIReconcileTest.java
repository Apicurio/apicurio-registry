package io.apicurio.registry.operator.mock;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;

/**
 * Mock-server equivalent of {@code DisableUIITTest}.
 */
@QuarkusTest
public class DisableUIReconcileTest extends MockServerTestBase {

    @Test
    void disableUI() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getSpec().getApp().getIngress().setHost("app.example.com");
        registry.getSpec().getUi().getIngress().setHost("ui.example.com");
        createRegistry(registry);

        // Everything should be created
        awaitDeploymentExists(deploymentName(registry, COMPONENT_APP));
        awaitDeploymentExists(deploymentName(registry, COMPONENT_UI));
        awaitServiceExists(serviceName(registry, COMPONENT_APP));
        awaitServiceExists(serviceName(registry, COMPONENT_UI));
        awaitIngressExists(ingressName(registry, COMPONENT_APP));
        awaitIngressExists(ingressName(registry, COMPONENT_UI));

        // Disable UI
        updateRegistry(registry, r -> r.getSpec().getUi().setEnabled(false));

        awaitDeploymentExists(deploymentName(registry, COMPONENT_APP));
        awaitDeploymentAbsent(deploymentName(registry, COMPONENT_UI));
        awaitServiceExists(serviceName(registry, COMPONENT_APP));
        awaitServiceAbsent(serviceName(registry, COMPONENT_UI));
        awaitIngressExists(ingressName(registry, COMPONENT_APP));
        awaitIngressAbsent(ingressName(registry, COMPONENT_UI));

        // Re-enable UI
        updateRegistry(registry, r -> r.getSpec().getUi().setEnabled(true));

        awaitDeploymentExists(deploymentName(registry, COMPONENT_APP));
        awaitDeploymentExists(deploymentName(registry, COMPONENT_UI));
        awaitServiceExists(serviceName(registry, COMPONENT_APP));
        awaitServiceExists(serviceName(registry, COMPONENT_UI));
        awaitIngressExists(ingressName(registry, COMPONENT_APP));
        awaitIngressExists(ingressName(registry, COMPONENT_UI));
    }
}
