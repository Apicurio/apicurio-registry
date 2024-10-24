package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.spec.Sql;
import io.apicurio.registry.operator.api.v1.spec.sql.Datasource;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
public class SqlDatasourceITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(SqlDatasourceITTest.class);

    @Test
    void testSqlDatasource() {
        client.load(SqlDatasourceITTest.class.getResourceAsStream("/k8s/example-postgres.yaml")).create();
        // await for postgres to be available
        await().ignoreExceptions().until(() -> (1 == client.apps().statefulSets().inNamespace(namespace)
                .withName("postgresql-db").get().getStatus().getReadyReplicas()));

        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);
        var sql = new Sql();
        registry.getSpec().getApp().setSql(sql);
        var datasource = new Datasource();
        sql.setDatasource(datasource);
        datasource.setUrl("jdbc:postgresql://postgres-db:5432/apicurio");
        datasource.setUsername("postgres-username");
        datasource.setPassword("postgres-password");

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
                    .contains("Database type: postgresql");
            return true;
        });
    }
}
