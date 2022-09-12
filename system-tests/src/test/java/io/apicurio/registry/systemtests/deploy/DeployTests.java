package io.apicurio.registry.systemtests.deploy;

import io.apicurio.registry.systemtests.TestBase;
import io.apicurio.registry.systemtests.registryinfra.resources.KafkaKind;
import io.apicurio.registry.systemtests.registryinfra.resources.PersistenceKind;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtensionContext;

@Disabled
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class DeployTests extends TestBase {
    /* TESTS - PostgreSQL */

    @Test
    public void testRegistrySqlNoIAM(ExtensionContext testContext) throws InterruptedException {
        deployTestRegistry(testContext, PersistenceKind.SQL, null, false);
    }

    @Test
    public void testRegistrySqlKeycloak(ExtensionContext testContext) throws InterruptedException {
        deployTestRegistry(testContext, PersistenceKind.SQL, null, true);
    }

    /* TESTS - KafkaSQL */

    @Test
    public void testRegistryKafkasqlNoAuthNoIAM(ExtensionContext testContext) throws InterruptedException {
        deployTestRegistry(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH, false);
    }

    @Test
    public void testRegistryKafkasqlNoAuthKeycloak(ExtensionContext testContext) throws InterruptedException {
        deployTestRegistry(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH, true);
    }

    @Test
    public void testRegistryKafkasqlTLSNoIAM(ExtensionContext testContext) throws InterruptedException {
        deployTestRegistry(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.TLS, false);
    }

    @Test
    public void testRegistryKafkasqlTLSKeycloak(ExtensionContext testContext) throws InterruptedException {
        deployTestRegistry(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.TLS, true);
    }

    @Test
    public void testRegistryKafkasqlSCRAMNoIAM(ExtensionContext testContext) throws InterruptedException {
        deployTestRegistry(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM, false);
    }

    @Test
    public void testRegistryKafkasqlSCRAMKeycloak(ExtensionContext testContext) throws InterruptedException {
        deployTestRegistry(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM, true);
    }
}
