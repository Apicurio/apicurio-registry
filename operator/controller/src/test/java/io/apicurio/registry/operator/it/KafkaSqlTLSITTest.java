package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.operator.Tags.KAFKA;
import static io.apicurio.registry.operator.Tags.SLOW;
import static io.apicurio.registry.operator.resource.ResourceFactory.deserialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
@Tag(KAFKA)
@Tag(SLOW)
public class KafkaSqlTLSITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(KafkaSqlTLSITTest.class);

    @BeforeAll
    public void beforeAll() throws Exception {
        applyStrimziResources();
    }

    @Test
    void testKafkaSQLTLS() {
        client.load(getClass().getResourceAsStream("/k8s/examples/kafkasql/tls/example-cluster.kafka.yaml"))
                .create();
        final var clusterName = "example-cluster";

        waitForKafkaBrokerReady(clusterName);

        client.load(getClass().getResourceAsStream("/k8s/examples/kafkasql/tls/apicurio.kafkauser.yaml"))
                .inNamespace(namespace).create();

        final var userName = "apicurio";

        await().untilAsserted(
                () -> assertThat(client.secrets().inNamespace(namespace).withName(userName).get())
                        .isNotNull());

        // We're guessing the value here to avoid using Strimzi Java model, and relying on retries below.
        var bootstrapServers = clusterName + "-kafka-bootstrap." + namespace + ".svc:9093";

        var registry = deserialize("k8s/examples/kafkasql/tls/example-kafkasql-tls.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);
        registry.getSpec().getApp().getStorage().getKafkasql().setBootstrapServers(bootstrapServers);

        client.resource(registry).create();

        waitForKafkaSqlRegistryReady(registry);
    }
}
