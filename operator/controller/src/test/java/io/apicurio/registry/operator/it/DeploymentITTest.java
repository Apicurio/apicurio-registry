package io.apicurio.registry.operator.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.apicur.registry.v1.ApicurioRegistry;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class DeploymentITTest extends ITBase {

  @Test
  void demoDeployment() {
    // Arrange
    var registry = new ApicurioRegistry();
    var meta = new ObjectMeta();
    meta.setName("demo");
    meta.setNamespace(getNamespace());
    registry.setMetadata(meta);

    // Act
    client.resources(ApicurioRegistry.class).inNamespace(getNamespace()).create(registry);

    // Assert
    await()
        .ignoreExceptions()
        .until(
            () -> {
              assertThat(
                      client
                          .apps()
                          .deployments()
                          .inNamespace(getNamespace())
                          .withName("demo")
                          .get()
                          .getStatus()
                          .getReadyReplicas())
                  .isEqualTo(1);
              return true;
            });
  }
}
