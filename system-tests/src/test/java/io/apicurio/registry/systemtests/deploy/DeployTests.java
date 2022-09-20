package io.apicurio.registry.systemtests.deploy;

import io.apicurio.registry.systemtests.TestBase;
import io.apicurio.registry.systemtests.registryinfra.resources.KafkaKind;
import io.apicurio.registry.systemtests.registryinfra.resources.PersistenceKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class DeployTests extends TestBase {
    /* TESTS - PostgreSQL */

    @Test
    public void testRegistrySqlNoIAM() throws InterruptedException {
        deployTestRegistry(PersistenceKind.SQL, null, false);
    }

    @Test
    public void testRegistrySqlKeycloak() throws InterruptedException {
        deployTestRegistry(PersistenceKind.SQL, null, true);
    }

    /* TESTS - KafkaSQL */

    @Test
    public void testRegistryKafkasqlNoAuthNoIAM() throws InterruptedException {
        deployTestRegistry(PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH, false);
    }

    @Test
    public void testRegistryKafkasqlNoAuthKeycloak() throws InterruptedException {
        deployTestRegistry(PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH, true);
    }

    @Test
    public void testRegistryKafkasqlTLSNoIAM() throws InterruptedException {
        deployTestRegistry(PersistenceKind.KAFKA_SQL, KafkaKind.TLS, false);
    }

    @Test
    public void testRegistryKafkasqlTLSKeycloak() throws InterruptedException {
        deployTestRegistry(PersistenceKind.KAFKA_SQL, KafkaKind.TLS, true);
    }

    @Test
    public void testRegistryKafkasqlSCRAMNoIAM() throws InterruptedException {
        deployTestRegistry(PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM, false);
    }

    @Test
    public void testRegistryKafkasqlSCRAMKeycloak() throws InterruptedException {
        deployTestRegistry(PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM, true);
    }
}
