package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.operator.Tags.AUTH;
import static io.apicurio.registry.operator.Tags.KAFKA;
import static io.apicurio.registry.operator.Tags.SLOW;
import static io.apicurio.registry.operator.resource.ResourceFactory.deserialize;

@QuarkusTest
@Tag(KAFKA)
@Tag(AUTH)
@Tag(SLOW)
public class KafkaSqlOAuthITTest extends BaseAuthITTest {

    private static final Logger log = LoggerFactory.getLogger(KafkaSqlOAuthITTest.class);

    @BeforeAll
    public void beforeAll() throws Exception {
        if (!strimziInstalled) {
            applyStrimziResources();
        }
    }

    @Test
    void testKafkaSQLOauth() {
        installKeycloak("/k8s/examples/auth/keycloak_realm.yaml", "/k8s/examples/auth/keycloak_http.yaml");

        client.load(getClass().getResourceAsStream("/k8s/examples/kafkasql/oauth/oauth-example-cluster.yaml"))
                .create();
        final var clusterName = "oauth-example-cluster";

        waitForKafkaBrokerReady(clusterName);

        // We're guessing the value here to avoid using Strimzi Java model, and relying on retries below.
        var bootstrapServers = clusterName + "-kafka-bootstrap." + namespace + ".svc:9093";

        var registry = deserialize(
                "k8s/examples/kafkasql/oauth/oauth-example-kafkasql-tls.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);
        registry.getSpec().getApp().getStorage().getKafkasql().setBootstrapServers(bootstrapServers);

        client.resource(registry).create();

        waitForKafkaSqlRegistryReady(registry);
    }
}
