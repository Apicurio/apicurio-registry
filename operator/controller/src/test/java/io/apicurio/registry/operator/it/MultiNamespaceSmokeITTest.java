package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
public class MultiNamespaceSmokeITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(MultiNamespaceSmokeITTest.class);

    private static ApicurioRegistry3 createRegistry(String namespace) {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml", ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);
        registry.getSpec().getApp().getIngress().setHost(ingressManager.getIngressHost(namespace, "app"));
        registry.getSpec().getUi().getIngress().setHost(ingressManager.getIngressHost(namespace, "ui"));
        client.resource(registry).create();
        return registry;
    }


    @Test
    void smoke() {

        var namespace2 = calculateNamespace();
        createNamespace(client, namespace2);
        var namespace3 = calculateNamespace();
        createNamespace(client, namespace3);

        try {
            var registry1 = createRegistry(namespace);
            var registry2 = createRegistry(namespace2);
            var registry3 = createRegistry(namespace3);

            checkDeploymentExists(registry1, COMPONENT_APP, 1);
            checkDeploymentExists(registry1, COMPONENT_UI, 1);

            checkDeploymentExists(registry2, COMPONENT_APP, 1);
            checkDeploymentExists(registry2, COMPONENT_UI, 1);

            checkDeploymentExists(registry3, COMPONENT_APP, 1);
            checkDeploymentExists(registry3, COMPONENT_UI, 1);

            checkServiceExists(registry1, COMPONENT_APP);
            checkServiceExists(registry1, COMPONENT_UI);

            checkServiceExists(registry2, COMPONENT_APP);
            checkServiceExists(registry2, COMPONENT_UI);

            checkServiceExists(registry3, COMPONENT_APP);
            checkServiceExists(registry3, COMPONENT_UI);

            checkIngressExists(registry1, COMPONENT_APP);
            checkIngressExists(registry1, COMPONENT_UI);

            checkIngressExists(registry2, COMPONENT_APP);
            checkIngressExists(registry2, COMPONENT_UI);

            checkIngressExists(registry3, COMPONENT_APP);
            checkIngressExists(registry3, COMPONENT_UI);

        } finally {
            if (cleanup) {
                var namespaces = List.of(namespace2, namespace3);
                namespaces.forEach(n -> {
                    log.info("Deleting namespace: {}", n);
                    client.namespaces().withName(n).delete();
                });
                // We need to wait until the finalizers run before undeploying the operator
                await().atMost(MEDIUM_DURATION).ignoreExceptions().untilAsserted(() -> {
                    namespaces.forEach(n -> assertThat(client.namespaces().withName(n).get()).isNull());
                });
            }
        }
    }
}
