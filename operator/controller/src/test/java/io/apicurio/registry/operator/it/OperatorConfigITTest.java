package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.operator.Tags.FEATURE;
import static io.apicurio.registry.operator.Tags.FEATURE_SETUP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
@Tag(FEATURE)
@Tag(FEATURE_SETUP)
public class OperatorConfigITTest extends ITBase {

    @Test
    @DisabledIf("io.apicurio.registry.operator.it.ITBase#isLocalDeployment")
    void testOperatorConfig() {
        var configMap = ResourceFactory
                .deserialize("/k8s/examples/config/operator-config.configmap.yaml", ConfigMap.class);
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml", ApicurioRegistry3.class);

        client.resource(registry).create();

        // TODO: Use PodLogManager
        await().atMost(LONG_DURATION).ignoreExceptions().untilAsserted(() -> {
            var operatorPod = waitOnOperatorPodReady();
            String log = client.pods().withName(operatorPod.getMetadata().getName()).getLog();
            assertThat(log).contains("No operator ConfigMap found.");
            // Create the ConfigMap and restart the pod
            client.resource(configMap).create();
            client.resource(operatorPod).delete();
        });

        await().atMost(LONG_DURATION).ignoreExceptions().untilAsserted(() -> {
            var operatorPod = waitOnOperatorPodReady();
            String log = client.pods().withName(operatorPod.getMetadata().getName()).getLog();
            assertThat(log).contains("Operator ConfigMap found, loaded 2 configuration options.");
        });
    }
}
