package io.apicurio.registry.operator.unit;

import static org.assertj.core.api.Assertions.assertThat;

import io.apicur.registry.v1.ApicurioRegistry;
import io.apicur.registry.v1.ApicurioRegistryBuilder;
import io.apicur.registry.v1.apicurioregistrystatus.Conditions;
import io.apicurio.registry.operator.StatusUpdater;
import org.junit.jupiter.api.Test;

public class StatusUpdaterTest {

  private ApicurioRegistry defaultRegistry =
      new ApicurioRegistryBuilder()
          .withNewMetadata()
          .withName("dummy")
          .withNamespace("default")
          .endMetadata()
          .build();

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
