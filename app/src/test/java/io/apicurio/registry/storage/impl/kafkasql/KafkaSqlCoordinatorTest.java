package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.types.RegistryException;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaSqlCoordinatorTest {

    @Test
    void testWaitForResponseTimesOut() {
        KafkaSqlConfiguration configuration = new KafkaSqlConfiguration();
        configuration.responseTimeout = 1;

        Instance<KafkaSqlConfiguration> configurationInstance = mock(Instance.class);
        when(configurationInstance.get()).thenReturn(configuration);

        KafkaSqlCoordinator coordinator = new KafkaSqlCoordinator();
        coordinator.configuration = configurationInstance;

        UUID uuid = coordinator.createUUID();

        RegistryException exception = assertThrows(
                RegistryException.class,
                () -> coordinator.waitForResponse(uuid));

        assertTrue(exception.getMessage().contains(uuid.toString()));
        assertTrue(exception.getMessage().contains("Timed out waiting for a Kafka Sql response"));
    }
}