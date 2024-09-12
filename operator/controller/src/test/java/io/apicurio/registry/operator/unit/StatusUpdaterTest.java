package io.apicurio.registry.operator.unit;

import io.apicurio.registry.operator.StatusUpdater;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.status.ConditionStatus;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StatusUpdaterTest {

    private static final ApicurioRegistry3 defaultRegistry = new ApicurioRegistry3();

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
        assertThat(status.getConditions().get(0).getStatus()).isEqualTo(ConditionStatus.TRUE);
        assertThat(status.getConditions().get(0).getType()).isEqualTo("ERROR");
    }
}
