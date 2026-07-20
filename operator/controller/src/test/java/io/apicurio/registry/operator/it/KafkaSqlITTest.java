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

@QuarkusTest
@Tag(KAFKA)
@Tag(SLOW)
public class KafkaSqlITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(KafkaSqlITTest.class);

    @BeforeAll
    public void beforeAll() throws Exception {
        applyStrimziResources();
    }

    @Test
    void testKafkaSQLPlain() {
        client.load(KafkaSqlITTest.class
                .getResourceAsStream("/k8s/examples/kafkasql/plain/example-cluster.kafka.yaml")).create();
        final var clusterName = "example-cluster";

        waitForKafkaBrokerReady(clusterName);

        // We're guessing the value here to avoid using Strimzi Java model, and relying on retries below.
        var bootstrapServers = clusterName + "-kafka-bootstrap." + namespace + ".svc:9092";

        var registry = deserialize(
                "k8s/examples/kafkasql/plain/example-kafkasql-plain.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);
        registry.getSpec().getApp().getStorage().getKafkasql().setBootstrapServers(bootstrapServers);

        client.resource(registry).create();

        waitForKafkaSqlRegistryReady(registry);
    }
}
