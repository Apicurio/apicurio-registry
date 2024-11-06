package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.spec.Sql;
import io.apicurio.registry.operator.api.v1.spec.sql.Datasource;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.apicurio.registry.operator.resource.ResourceFactory.APP_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.ResourceFactory.UI_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.getContainerFromDeployment;
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
            assertThat(status.getConditions().stream().anyMatch(c -> c.getType().equalsIgnoreCase("ready"))).isTrue();
            assertThat(status.getConditions().stream().anyMatch(c -> c.getType().equalsIgnoreCase("started"))).isTrue();
            return true;
        });
    }

    @Test
    void testErrorStatusIsTriggered() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);
        // dummy settings to avoid reaching the READY state
        var sql = new Sql();
        var datasource = new Datasource();
        sql.setDatasource(datasource);
        datasource.setUrl("dummy");
        datasource.setUsername("dummy");
        datasource.setPassword("dummy");
        registry.getSpec().getApp().setSql(sql);

        client.resource(registry).create();

        await().ignoreExceptions().until(() -> {
            var status = client.resource(registry).inNamespace(namespace).get().getStatus();
            assertThat(status.getConditions().size()).isEqualTo(1);
            assertThat(status.getConditions().stream().anyMatch(c -> c.getType().equalsIgnoreCase("started"))).isTrue();
            return true;
        });
    }
}
