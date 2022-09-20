package io.apicurio.registry.systemtests.api;

import io.apicur.registry.v1.ApicurioRegistry;
import io.apicurio.registry.systemtests.TestBase;
import io.apicurio.registry.systemtests.api.features.CreateArtifact;
import io.apicurio.registry.systemtests.api.features.CreateReadDelete;
import io.apicurio.registry.systemtests.framework.Constants;
import io.apicurio.registry.systemtests.registryinfra.resources.KafkaKind;
import io.apicurio.registry.systemtests.registryinfra.resources.PersistenceKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class APITests extends TestBase {
    /* TEST RUNNERS */

    protected void runCreateReadDeleteTest(
            PersistenceKind persistenceKind,
            KafkaKind kafkaKind,
            boolean useKeycloak
    ) throws InterruptedException {
        ApicurioRegistry registry = deployTestRegistry(persistenceKind, kafkaKind, useKeycloak);

        if (useKeycloak) {
            CreateReadDelete.testCreateReadDelete(
                    registry,
                    Constants.SSO_ADMIN_USER,
                    Constants.SSO_USER_PASSWORD,
                    true
            );
        } else {
            CreateReadDelete.testCreateReadDelete(registry, null, null, false);
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
    public void testRegistrySqlNoIAMCreateReadDelete() throws InterruptedException {
        runCreateReadDeleteTest(PersistenceKind.SQL, null, false);
    }

    @Test
    public void testRegistrySqlKeycloakCreateReadDelete() throws InterruptedException {
        runCreateReadDeleteTest(PersistenceKind.SQL, null, true);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    @Test
    public void testRegistrySqlNoIAMCreateArtifact() throws InterruptedException {
        runCreateArtifactTest(PersistenceKind.SQL, null, false);
    }

    @Test
    public void testRegistrySqlKeycloakCreateArtifact() throws InterruptedException {
        runCreateArtifactTest(PersistenceKind.SQL, null, true);
    }

    /* TESTS - KafkaSQL */

    @Test
    public void testRegistryKafkasqlNoAuthNoIAMCreateReadDelete() throws InterruptedException {
        runCreateReadDeleteTest(PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH, false);
    }

    @Test
    public void testRegistryKafkasqlNoAuthKeycloakCreateReadDelete() throws InterruptedException {
        runCreateReadDeleteTest(PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH, true);
    }

    @Test
    public void testRegistryKafkasqlTLSNoIAMCreateReadDelete() throws InterruptedException {
        runCreateReadDeleteTest(PersistenceKind.KAFKA_SQL, KafkaKind.TLS, false);
    }

    @Test
    public void testRegistryKafkasqlTLSKeycloakCreateReadDelete() throws InterruptedException {
        runCreateReadDeleteTest(PersistenceKind.KAFKA_SQL, KafkaKind.TLS, true);
    }

    @Test
    public void testRegistryKafkasqlSCRAMNoIAMCreateReadDelete() throws InterruptedException {
        runCreateReadDeleteTest(PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM, false);
    }

    @Test
    public void testRegistryKafkasqlSCRAMKeycloakCreateReadDelete() throws InterruptedException {
        runCreateReadDeleteTest(PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM, true);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    @Test
    public void testRegistryKafkasqlNoAuthNoIAMCreateArtifact() throws InterruptedException {
        runCreateArtifactTest(PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH, false);
    }

    @Test
    public void testRegistryKafkasqlNoAuthKeycloakCreateArtifact() throws InterruptedException {
        runCreateArtifactTest(PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH, true);
    }

    @Test
    public void testRegistryKafkasqlTLSNoIAMCreateArtifact() throws InterruptedException {
        runCreateArtifactTest(PersistenceKind.KAFKA_SQL, KafkaKind.TLS, false);
    }

    @Test
    public void testRegistryKafkasqlTLSKeycloakCreateArtifact() throws InterruptedException {
        runCreateArtifactTest(PersistenceKind.KAFKA_SQL, KafkaKind.TLS, true);
    }

    @Test
    public void testRegistryKafkasqlSCRAMNoIAMCreateArtifact() throws InterruptedException {
        runCreateArtifactTest(PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM, false);
    }

    @Test
    public void testRegistryKafkasqlSCRAMKeycloakCreateArtifact() throws InterruptedException {
        runCreateArtifactTest(PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM, true);
    }
}
