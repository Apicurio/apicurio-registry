package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;

@QuarkusTest
public class DisableUIITTest extends ITBase {

    @Test
    void disableUITest() {

        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);

        registry.getSpec().getApp().getIngress().setHost(ingressManager.getIngressHost(COMPONENT_APP));
        registry.getSpec().getUi().getIngress().setHost(ingressManager.getIngressHost(COMPONENT_UI));

        registry = client.resource(registry).create();

        checkDeploymentExists(registry, COMPONENT_APP, 1);
        checkDeploymentExists(registry, COMPONENT_UI, 1);
        checkServiceExists(registry, COMPONENT_APP);
        checkServiceExists(registry, COMPONENT_UI);
        checkIngressExists(registry, COMPONENT_APP);
        checkIngressExists(registry, COMPONENT_UI);

        registry = updateWithRetries(registry, r -> r.getSpec().getUi().setEnabled(false));

        checkDeploymentExists(registry, COMPONENT_APP, 1);
        checkDeploymentDoesNotExist(registry, COMPONENT_UI);
        checkServiceExists(registry, COMPONENT_APP);
        checkServiceDoesNotExist(registry, COMPONENT_UI);
        checkIngressExists(registry, COMPONENT_APP);
        checkIngressDoesNotExist(registry, COMPONENT_UI);

        registry = updateWithRetries(registry, r -> r.getSpec().getUi().setEnabled(true));

        checkDeploymentExists(registry, COMPONENT_APP, 1);
        checkDeploymentExists(registry, COMPONENT_UI, 1);
        checkServiceExists(registry, COMPONENT_APP);
        checkServiceExists(registry, COMPONENT_UI);
        checkIngressExists(registry, COMPONENT_APP);
        checkIngressExists(registry, COMPONENT_UI);
    }
}
