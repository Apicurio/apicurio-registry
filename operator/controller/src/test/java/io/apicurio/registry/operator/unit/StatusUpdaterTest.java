package io.apicurio.registry.operator.unit;

import static org.assertj.core.api.Assertions.assertThat;

import io.apicur.registry.v1.ApicurioRegistry;
import io.apicur.registry.v1.apicurioregistrystatus.Conditions;
import io.apicurio.registry.operator.StatusUpdater;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import org.junit.jupiter.api.Test;

public class StatusUpdaterTest {

  private static final ApicurioRegistry defaultRegistry = new ApicurioRegistry();

  static {
    var meta = new ObjectMeta();
    meta.setName("dummy");
    meta.setNamespace("default");
    defaultRegistry.setMetadata(meta);
  }

  @Test
  void shouldReturnAnErrorStatus() {
    // Arrange
    var su = new StatusUpdater(defaultRegistry);

    // Act
    var status = su.errorStatus(new RuntimeException("hello world"));

    // Assert
    assertThat(status).isNotNull();
    assertThat(status.getConditions()).singleElement();
    assertThat(status.getConditions().get(0).getStatus()).isEqualTo(Conditions.Status.TRUE);
    assertThat(status.getConditions().get(0).getType()).isEqualTo("ERROR");
  }
}
