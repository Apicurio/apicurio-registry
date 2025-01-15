package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.spec.DataSourceSpec;
import io.apicurio.registry.operator.api.v1.spec.SqlSpec;
import io.apicurio.registry.operator.api.v1.spec.StorageSpec;
import io.apicurio.registry.operator.api.v1.spec.StorageType;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
public class StatusUpdaterTest extends ITBase {

    @Test
    void testReadyStatusIsReached() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);

        client.resource(registry).create();

        await().ignoreExceptions().until(() -> {
            var status = client.resource(registry).inNamespace(namespace).get().getStatus();
            assertThat(status.getConditions().size()).isEqualTo(2);
            assertThat(status.getConditions().stream().anyMatch(c -> c.getType().equalsIgnoreCase("ready")))
                    .isTrue();
            assertThat(status.getConditions().stream().anyMatch(c -> c.getType().equalsIgnoreCase("started")))
                    .isTrue();
            return true;
        });
    }

    @Test
    void testErrorStatusIsTriggered() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);
        // dummy settings to avoid reaching the READY state

        // spotless:off
        registry.getSpec().getApp().setStorage(StorageSpec.builder()
                .type(StorageType.POSTGRESQL)
                .sql(SqlSpec.builder()
                        .dataSource(DataSourceSpec.builder()
                                .url("dummy")
                                .username("dummy")
                                .password("dummy")
                                .build())
                        .build())
                .build());
        // spotless:on

        client.resource(registry).create();

        await().ignoreExceptions().until(() -> {
            var status = client.resource(registry).inNamespace(namespace).get().getStatus();
            assertThat(status.getConditions().size()).isEqualTo(1);
            assertThat(status.getConditions().stream().anyMatch(c -> c.getType().equalsIgnoreCase("started")))
                    .isTrue();
            return true;
        });
    }
}
