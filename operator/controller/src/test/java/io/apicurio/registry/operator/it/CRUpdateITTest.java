package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

import static io.apicurio.registry.operator.utils.Utils.isBlank;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
public class CRUpdateITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(CRUpdateITTest.class);

    @Test
    void testCRUpdate() {

        var testCases = List.of(
                List.of(
                        "/k8s/examples/simple-deprecated.apicurioregistry3.yaml",
                        "/k8s/examples/simple.apicurioregistry3.yaml"
                ),
                List.of(
                        "/k8s/examples/postgresql/example-postgresql-deprecated.apicurioregistry3.yaml",
                        "/k8s/examples/postgresql/example-postgresql.apicurioregistry3.yaml"
                ),
                List.of(
                        "/k8s/examples/kafkasql/plain/example-kafkasql-plain-deprecated.apicurioregistry3.yaml",
                        "/k8s/examples/kafkasql/plain/example-kafkasql-plain.apicurioregistry3.yaml"
                )
        );

        testCases.forEach(testCase -> {

            var deprecated = ResourceFactory.deserialize(testCase.get(0), ApicurioRegistry3.class);
            var updatedExpected = ResourceFactory.deserialize(testCase.get(1), ApicurioRegistry3.class);

            client.resource(deprecated).create();

            await().ignoreExceptionsInstanceOf(KubernetesClientException.class)
                    .timeout(Duration.ofSeconds(60)).untilAsserted(() -> {
                        var updated = client
                                .resources(ApicurioRegistry3.class).list().getItems().stream().filter(r -> r
                                        .getMetadata().getName().equals(deprecated.getMetadata().getName()))
                                .toList();
                        assertThat(updated).hasSize(1);
                        // We do not care about the operand here, just about the CR structure
                        assertThat(updated.get(0).getSpec()).usingRecursiveComparison()
                                // We have to specially handle generated Secret name, since we do not know it in advance.
                                // It should be enough to just make sure it's not blank.
                                .withEqualsForFields((l, r) -> !isBlank((String) l) && !isBlank((String) r),
                                        "app.storage.sql.dataSource.password.name")
                                .isEqualTo(updatedExpected.getSpec());
                    });
        });
    }
}
