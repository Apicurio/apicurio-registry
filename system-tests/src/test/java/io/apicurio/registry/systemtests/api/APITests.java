package io.apicurio.registry.systemtests.api;

import io.apicur.registry.v1.ApicurioRegistry;
import io.apicurio.registry.systemtests.client.ApicurioRegistryApiClient;
import io.apicurio.registry.systemtests.client.ArtifactContent;
import io.apicurio.registry.systemtests.client.ArtifactList;
import io.apicurio.registry.systemtests.client.ArtifactType;
import io.apicurio.registry.systemtests.client.AuthMethod;
import io.apicurio.registry.systemtests.framework.ApicurioRegistryUtils;
import io.apicurio.registry.systemtests.framework.KeycloakUtils;
import io.apicurio.registry.systemtests.registryinfra.resources.KafkaKind;
import io.apicurio.registry.systemtests.registryinfra.resources.PersistenceKind;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtensionContext;

@Disabled
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class APITests extends TestBase {
    /* TEST RUNNERS */

    protected void runCreateReadDeleteTest(
            ExtensionContext testContext,
            PersistenceKind persistenceKind,
            KafkaKind kafkaKind,
            boolean useKeycloak
    ) {
        ApicurioRegistry registry = deployTestRegistry(testContext, persistenceKind, kafkaKind, useKeycloak);

        if (useKeycloak) {
            CreateReadDelete.testCreateReadDelete(registry, "registry-admin", "changeme", true);

            KeycloakUtils.removeKeycloak();
        } else {
            CreateReadDelete.testCreateReadDelete(registry, null, null, false);
        }
    }

    /* TESTS - PostgreSQL */

    @Test
    public void testRegistrySqlNoIAMCreateReadDelete(ExtensionContext testContext) {
        runCreateReadDeleteTest(testContext, PersistenceKind.SQL, null, false);
    }

    @Test
    public void testRegistrySqlKeycloakCreateReadDelete(ExtensionContext testContext) {
        runCreateReadDeleteTest(testContext, PersistenceKind.SQL, null, true);
    }

    /* TESTS - KafkaSQL */

    @Test
    public void testRegistryKafkasqlNoAuthNoIAMCreateReadDelete(ExtensionContext testContext) {
        runCreateReadDeleteTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH, false);
    }

    @Test
    public void testRegistryKafkasqlNoAuthKeycloakCreateReadDelete(ExtensionContext testContext) {
        runCreateReadDeleteTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH, true);
    }

    @Test
    public void testRegistryKafkasqlTLSNoIAMCreateReadDelete(ExtensionContext testContext) {
        runCreateReadDeleteTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.TLS, false);
    }

    @Test
    public void testRegistryKafkasqlTLSKeycloakCreateReadDelete(ExtensionContext testContext) {
        runCreateReadDeleteTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.TLS, true);
    }

    @Test
    public void testRegistryKafkasqlSCRAMNoIAMCreateReadDelete(ExtensionContext testContext) {
        runCreateReadDeleteTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM, false);
    }

    @Test
    public void testRegistryKafkasqlSCRAMKeycloakCreateReadDelete(ExtensionContext testContext) {
        runCreateReadDeleteTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM, true);
    }
}
