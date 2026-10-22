package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.operator.Tags.DATABASE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for MySQL datasource configuration.
 */
@QuarkusTest
@Tag(DATABASE)
public class MysqlDataSourceITTest extends ITBase {

    @Test
    void testMysqlDatasource() {
        client.load(MysqlDataSourceITTest.class
                .getResourceAsStream("/k8s/examples/mysql/example-mysql-database.yaml")).create();
        // await for MySQL to be available
        await().atMost(DATABASE_TIMEOUT).untilAsserted(() -> {
            var database = client.apps().statefulSets().inNamespace(namespace)
                    .withName("example-mysql-database").get();
            assertThat(database).isNotNull();
            assertThat(database.getStatus()).isNotNull();
            assertThat(database.getStatus().getReadyReplicas()).isEqualTo(1);
        });

        var registry = ResourceFactory.deserialize(
                "/k8s/examples/mysql/example-mysql.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);

        client.resource(registry).create();

        await().atMost(DATABASE_TIMEOUT).untilAsserted(() -> {
            var deployment = client.apps().deployments().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-app-deployment").get();
            assertThat(deployment).isNotNull();
            assertThat(deployment.getStatus()).isNotNull();
            assertThat(deployment.getStatus().getReadyReplicas()).isEqualTo(1);
            var podName = client.pods().inNamespace(namespace).list().getItems().stream()
                    .map(pod -> pod.getMetadata().getName())
                    .filter(podN -> podN.startsWith(registry.getMetadata().getName() + "-app-deployment"))
                    .findFirst();
            assertThat(podName).isPresent();
            // Emitted on every startup, including after a restart with an initialized database.
            assertThat(client.pods().inNamespace(namespace).withName(podName.orElseThrow()).getLog())
                    .contains("Using mysql SQL storage.");
        });
    }
}
