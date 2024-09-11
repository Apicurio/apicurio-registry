package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
public class DeploymentITTest extends ITBase {

    @Test
    void demoDeployment() {
        // Arrange
        var registry = new ApicurioRegistry3();
        var meta = new ObjectMeta();
        meta.setName("demo");
        meta.setNamespace(getNamespace());
        registry.setMetadata(meta);

        // Act
        client.resources(ApicurioRegistry3.class).inNamespace(getNamespace()).create(registry);

        // Assert
        await().ignoreExceptions().until(() -> {
            assertThat(client.apps().deployments().inNamespace(getNamespace()).withName("demo-app-deployment")
                    .get().getStatus().getReadyReplicas()).isEqualTo(1);
            assertThat(client.apps().deployments().inNamespace(getNamespace()).withName("demo-ui-deployment")
                    .get().getStatus().getReadyReplicas()).isEqualTo(1);
            return true;
        });
    }
}
