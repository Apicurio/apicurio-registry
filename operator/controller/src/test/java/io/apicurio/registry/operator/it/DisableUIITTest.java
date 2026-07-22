package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.apicurio.registry.operator.utils.OperatorTestExtension;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.apicurio.registry.operator.Tags.FEATURE;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;
import static io.apicurio.registry.operator.utils.K8sCell.k8sCellCreate;

@QuarkusTest
@ExtendWith({ OperatorInfraExtension.class, OperatorTestExtension.class })
@Tag(FEATURE)
public class DisableUIITTest {

    @Test
    void disableUITest(KubernetesClient client, IngressManager ingressManager,
            RegistryAssertions check) {

        final var registry = k8sCellCreate(client, () -> {
            var r = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml", ApicurioRegistry3.class);

            r.getSpec().getApp().getIngress().setHost(ingressManager.getIngressHost(COMPONENT_APP));
            r.getSpec().getUi().getIngress().setHost(ingressManager.getIngressHost(COMPONENT_UI));

            return r;
        });

        check.checkDeploymentExists(registry.getCached(), COMPONENT_APP, 1);
        check.checkDeploymentExists(registry.getCached(), COMPONENT_UI, 1);
        check.checkServiceExists(registry.getCached(), COMPONENT_APP);
        check.checkServiceExists(registry.getCached(), COMPONENT_UI);
        check.checkIngressExists(registry.getCached(), COMPONENT_APP);
        check.checkIngressExists(registry.getCached(), COMPONENT_UI);

        registry.update(r -> {
            r.getSpec().getUi().setEnabled(false);
        });

        check.checkDeploymentExists(registry.getCached(), COMPONENT_APP, 1);
        check.checkDeploymentDoesNotExist(registry.getCached(), COMPONENT_UI);
        check.checkServiceExists(registry.getCached(), COMPONENT_APP);
        check.checkServiceDoesNotExist(registry.getCached(), COMPONENT_UI);
        check.checkIngressExists(registry.getCached(), COMPONENT_APP);
        check.checkIngressDoesNotExist(registry.getCached(), COMPONENT_UI);

        registry.update(r -> {
            r.getSpec().getUi().setEnabled(true);
        });

        check.checkDeploymentExists(registry.getCached(), COMPONENT_APP, 1);
        check.checkDeploymentExists(registry.getCached(), COMPONENT_UI, 1);
        check.checkServiceExists(registry.getCached(), COMPONENT_APP);
        check.checkServiceExists(registry.getCached(), COMPONENT_UI);
        check.checkIngressExists(registry.getCached(), COMPONENT_APP);
        check.checkIngressExists(registry.getCached(), COMPONENT_UI);
    }
}
