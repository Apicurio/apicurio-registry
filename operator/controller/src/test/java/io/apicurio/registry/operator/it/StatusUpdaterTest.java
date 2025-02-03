package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.spec.DataSourceSpec;
import io.apicurio.registry.operator.api.v1.spec.SqlSpec;
import io.apicurio.registry.operator.api.v1.spec.StorageSpec;
import io.apicurio.registry.operator.api.v1.spec.StorageType;
import io.apicurio.registry.operator.api.v1.status.Condition;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.operator.StatusUpdater.READY_TYPE;
import static io.apicurio.registry.operator.StatusUpdater.STARTED_TYPE;
import static io.apicurio.registry.operator.api.v1.status.ConditionStatus.*;
import static io.apicurio.registry.operator.api.v1.status.ConditionStatus.TRUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
public class StatusUpdaterTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(StatusUpdaterTest.class);

    @Test
    void testReadyStatusIsReached() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);

        client.resource(registry).create();

        await().ignoreExceptions().untilAsserted(() -> {
            var status = client.resource(registry).get().getStatus();
            assertThat(status.getConditions()).hasSize(2);
            assertThat(status.getConditions().stream().filter(c -> READY_TYPE.equals(c.getType())))
                    .map(Condition::getStatus).containsExactly(TRUE);
            assertThat(status.getConditions().stream().filter(c -> STARTED_TYPE.equals(c.getType())))
                    .map(Condition::getStatus).containsExactly(TRUE);
        });
    }

    @Test
    void testErrorStatusIsTriggered() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);
        // dummy settings to avoid reaching the READY state

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

        client.resource(registry).create();

        await().ignoreExceptions().untilAsserted(() -> {
            var status = client.resource(registry).get().getStatus();
            assertThat(status.getConditions()).hasSize(2);
            assertThat(status.getConditions().stream().filter(c -> READY_TYPE.equals(c.getType())))
                    .map(Condition::getStatus).containsExactly(FALSE);
            assertThat(status.getConditions().stream().filter(c -> STARTED_TYPE.equals(c.getType())))
                    .map(Condition::getStatus).containsExactly(TRUE);
        });
    }
}
