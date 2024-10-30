package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.operator.it.KafkaSQLITTest.applyStrimziResources;
import static io.apicurio.registry.operator.resource.ResourceFactory.deserialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
public class KafkaSQLTLSITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(KafkaSQLTLSITTest.class);

    @BeforeAll
    public static void beforeAll() throws Exception {
        applyStrimziResources();
    }

    @Test
    void testKafkaSQLTLS() {
        client.load(getClass().getResourceAsStream("/k8s/examples/kafkasql/tls/example-cluster.kafka.yaml"))
                .create();
        final var clusterName = "example-cluster";

        await().ignoreExceptions().untilAsserted(() ->
        // Strimzi uses StrimziPodSet instead of ReplicaSet, so we have to check pods
        assertThat(client.pods().inNamespace(namespace).withName(clusterName + "-kafka-0").get().getStatus()
                .getConditions()).filteredOn(c -> "Ready".equals(c.getType())).map(PodCondition::getStatus)
                .containsOnly("True"));

        client.load(getClass().getResourceAsStream("/k8s/examples/kafkasql/tls/apicurio.kafkauser.yaml"))
                .inNamespace(namespace).create();

        final var userName = "apicurio";

        await().untilAsserted(
                () -> assertThat(client.secrets().inNamespace(namespace).withName(userName).get())
                        .isNotNull());

        // We're guessing the value here to avoid using Strimzi Java model, and relying on retries below.
        var bootstrapServers = clusterName + "-kafka-bootstrap." + namespace + ".svc:9093";

        var registry = deserialize("k8s/examples/kafkasql/tls/kafkasql-tls.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);
        registry.getSpec().getApp().getKafkasql().setBootstrapServers(bootstrapServers);

        client.resource(registry).create();

        await().ignoreExceptions().until(() -> {
            assertThat(client.apps().deployments().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-app-deployment").get().getStatus()
                    .getReadyReplicas().intValue()).isEqualTo(1);
            var podName = client.pods().inNamespace(namespace).list().getItems().stream()
                    .map(pod -> pod.getMetadata().getName())
                    .filter(podN -> podN.startsWith(registry.getMetadata().getName() + "-app-deployment"))
                    .findFirst().get();
            assertThat(client.pods().inNamespace(namespace).withName(podName).getLog())
                    .contains("Using Kafka-SQL artifactStore");
            return true;
        });
    }
}
