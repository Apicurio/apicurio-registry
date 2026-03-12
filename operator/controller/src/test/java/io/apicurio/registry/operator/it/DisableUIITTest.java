package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.operator.Tags.FEATURE;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;
import static io.apicurio.registry.operator.utils.K8sCell.k8sCellCreate;

@QuarkusTest
@Tag(FEATURE)
public class DisableUIITTest extends ITBase {

    @Test
    void disableUITest() {

        final var registry = k8sCellCreate(client, () -> {
            var r = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml", ApicurioRegistry3.class);

            r.getSpec().getApp().getIngress().setHost(ingressManager.getIngressHost(COMPONENT_APP));
            r.getSpec().getUi().getIngress().setHost(ingressManager.getIngressHost(COMPONENT_UI));

            return r;
        });

        checkDeploymentExists(registry.getCached(), COMPONENT_APP, 1);
        checkDeploymentExists(registry.getCached(), COMPONENT_UI, 1);
        checkServiceExists(registry.getCached(), COMPONENT_APP);
        checkServiceExists(registry.getCached(), COMPONENT_UI);
        checkIngressExists(registry.getCached(), COMPONENT_APP);
        checkIngressExists(registry.getCached(), COMPONENT_UI);

        registry.update(r -> {
            r.getSpec().getUi().setEnabled(false);
        });

        checkDeploymentExists(registry.getCached(), COMPONENT_APP, 1);
        checkDeploymentDoesNotExist(registry.getCached(), COMPONENT_UI);
        checkServiceExists(registry.getCached(), COMPONENT_APP);
        checkServiceDoesNotExist(registry.getCached(), COMPONENT_UI);
        checkIngressExists(registry.getCached(), COMPONENT_APP);
        checkIngressDoesNotExist(registry.getCached(), COMPONENT_UI);

        registry.update(r -> {
            r.getSpec().getUi().setEnabled(true);
        });

        checkDeploymentExists(registry.getCached(), COMPONENT_APP, 1);
        checkDeploymentExists(registry.getCached(), COMPONENT_UI, 1);
        checkServiceExists(registry.getCached(), COMPONENT_APP);
        checkServiceExists(registry.getCached(), COMPONENT_UI);
        checkIngressExists(registry.getCached(), COMPONENT_APP);
        checkIngressExists(registry.getCached(), COMPONENT_UI);
    }
}
