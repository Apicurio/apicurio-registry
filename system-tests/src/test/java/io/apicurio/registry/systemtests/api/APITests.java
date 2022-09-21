package io.apicurio.registry.systemtests.api;

import io.apicur.registry.v1.ApicurioRegistry;
import io.apicurio.registry.systemtests.TestBase;
import io.apicurio.registry.systemtests.api.features.CreateArtifact;
import io.apicurio.registry.systemtests.api.features.CreateReadUpdateDelete;
import io.apicurio.registry.systemtests.framework.Constants;
import io.apicurio.registry.systemtests.registryinfra.resources.KafkaKind;
import io.apicurio.registry.systemtests.registryinfra.resources.PersistenceKind;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class APITests extends TestBase {
    /* TEST RUNNERS */

    protected void runCreateReadUpdateDeleteTest(
            PersistenceKind persistenceKind,
            KafkaKind kafkaKind,
            boolean useKeycloak
    ) throws InterruptedException {
        ApicurioRegistry registry = deployTestRegistry(persistenceKind, kafkaKind, useKeycloak);

        if (useKeycloak) {
            CreateReadUpdateDelete.testCreateReadUpdateDelete(
                    registry,
                    Constants.SSO_ADMIN_USER,
                    Constants.SSO_USER_PASSWORD,
                    true
            );
        } else {
            CreateReadUpdateDelete.testCreateReadUpdateDelete(registry, null, null, false);
        }
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    protected void runCreateArtifactTest(
            PersistenceKind persistenceKind,
            KafkaKind kafkaKind,
            boolean useKeycloak
    ) throws InterruptedException {
        ApicurioRegistry registry = deployTestRegistry(persistenceKind, kafkaKind, useKeycloak);

        if (useKeycloak) {
            CreateArtifact.testCreateArtifact(registry, Constants.SSO_ADMIN_USER, Constants.SSO_USER_PASSWORD, true);
        } else {
            CreateArtifact.testCreateArtifact(registry, null, null, false);
        }
    }

    /* TESTS - PostgreSQL */

    @Test
    public void testRegistrySqlNoIAMCreateReadUpdateDelete() throws InterruptedException {
        runCreateReadUpdateDeleteTest(PersistenceKind.SQL, null, false);
    }

    @Test
    public void testRegistrySqlKeycloakCreateReadUpdateDelete() throws InterruptedException {
        runCreateReadUpdateDeleteTest(PersistenceKind.SQL, null, true);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    @Test
    @Disabled
    public void testRegistrySqlNoIAMCreateArtifact() throws InterruptedException {
        runCreateArtifactTest(PersistenceKind.SQL, null, false);
    }

    @Test
    @Disabled
    public void testRegistrySqlKeycloakCreateArtifact() throws InterruptedException {
        runCreateArtifactTest(PersistenceKind.SQL, null, true);
    }

    /* TESTS - KafkaSQL */

    @Test
    public void testRegistryKafkasqlNoAuthNoIAMCreateReadUpdateDelete() throws InterruptedException {
        runCreateReadUpdateDeleteTest(PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH, false);
    }

    @Test
    public void testRegistryKafkasqlNoAuthKeycloakCreateReadUpdateDelete() throws InterruptedException {
        runCreateReadUpdateDeleteTest(PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH, true);
    }

    @Test
    public void testRegistryKafkasqlTLSNoIAMCreateReadUpdateDelete() throws InterruptedException {
        runCreateReadUpdateDeleteTest(PersistenceKind.KAFKA_SQL, KafkaKind.TLS, false);
    }

    @Test
    public void testRegistryKafkasqlTLSKeycloakCreateReadUpdateDelete() throws InterruptedException {
        runCreateReadUpdateDeleteTest(PersistenceKind.KAFKA_SQL, KafkaKind.TLS, true);
    }

    @Test
    public void testRegistryKafkasqlSCRAMNoIAMCreateReadUpdateDelete() throws InterruptedException {
        runCreateReadUpdateDeleteTest(PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM, false);
    }

    @Test
    public void testRegistryKafkasqlSCRAMKeycloakCreateReadUpdateDelete() throws InterruptedException {
        runCreateReadUpdateDeleteTest(PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM, true);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    @Test
    @Disabled
    public void testRegistryKafkasqlNoAuthNoIAMCreateArtifact() throws InterruptedException {
        runCreateArtifactTest(PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH, false);
    }

    @Test
    @Disabled
    public void testRegistryKafkasqlNoAuthKeycloakCreateArtifact() throws InterruptedException {
        runCreateArtifactTest(PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH, true);
    }

    @Test
    @Disabled
    public void testRegistryKafkasqlTLSNoIAMCreateArtifact() throws InterruptedException {
        runCreateArtifactTest(PersistenceKind.KAFKA_SQL, KafkaKind.TLS, false);
    }

    @Test
    @Disabled
    public void testRegistryKafkasqlTLSKeycloakCreateArtifact() throws InterruptedException {
        runCreateArtifactTest(PersistenceKind.KAFKA_SQL, KafkaKind.TLS, true);
    }

    @Test
    @Disabled
    public void testRegistryKafkasqlSCRAMNoIAMCreateArtifact() throws InterruptedException {
        runCreateArtifactTest(PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM, false);
    }

    @Test
    @Disabled
    public void testRegistryKafkasqlSCRAMKeycloakCreateArtifact() throws InterruptedException {
        runCreateArtifactTest(PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM, true);
    }
}
