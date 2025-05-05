package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
public class OperatorConfigITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(OperatorConfigITTest.class);

    @Test
    void testOperatorConfig() {
        if (operatorDeployment == OperatorDeployment.local) {
            log.warn("Test requires an operator pod, so it's not supported when running locally.");
            return;
        }

        var configMap = ResourceFactory
                .deserialize("/k8s/examples/config/operator-config.configmap.yaml", ConfigMap.class);
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml", ApicurioRegistry3.class);

        client.resource(registry).create();

        // TODO: Use PodLogManager
        await().atMost(MEDIUM_DURATION).ignoreExceptions().untilAsserted(() -> {
            var operatorPods = client.pods()
                    .withLabels(Map.of(
                            "app.kubernetes.io/name", "apicurio-registry-operator",
                            "app.kubernetes.io/component", "operator",
                            "app.kubernetes.io/part-of", "apicurio-registry"))
                    .list().getItems();
            assertThat(operatorPods).hasSize(1);
            String log = client.pods().withName(operatorPods.get(0).getMetadata().getName()).getLog();
            assertThat(log).contains("No operator ConfigMap found.");
            // Create the ConfigMap and restart the pod
            client.resource(configMap).create();
            client.resource(operatorPods.get(0)).delete();
        });

        await().atMost(MEDIUM_DURATION).ignoreExceptions().untilAsserted(() -> {
            var operatorPods = client.pods()
                    .withLabels(Map.of(
                            "app.kubernetes.io/name", "apicurio-registry-operator",
                            "app.kubernetes.io/component", "operator",
                            "app.kubernetes.io/part-of", "apicurio-registry"))
                    .list().getItems();
            assertThat(operatorPods).hasSize(1);
            String log = client.pods().withName(operatorPods.get(0).getMetadata().getName()).getLog();
            assertThat(log).contains("Operator ConfigMap found, loaded 2 configuration options.");
        });
    }
}
