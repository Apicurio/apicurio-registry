package io.apicurio.registry.systemtests.auth;

import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.systemtests.TestBase;
import io.apicurio.registry.systemtests.auth.features.AnonymousReadAccess;
import io.apicurio.registry.systemtests.auth.features.BasicAuthentication;
import io.apicurio.registry.systemtests.framework.KeycloakUtils;
import io.apicurio.registry.systemtests.registryinfra.resources.KafkaKind;
import io.apicurio.registry.systemtests.registryinfra.resources.PersistenceKind;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtensionContext;

@Disabled
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AuthTests extends TestBase {
    /* TEST RUNNERS */

    protected void runAnonymousReadAccessTest(
            ExtensionContext testContext,
            PersistenceKind persistenceKind,
            KafkaKind kafkaKind,
            boolean useKeycloak
    ) {
        ApicurioRegistry registry = deployTestRegistry(testContext, persistenceKind, kafkaKind, useKeycloak);

        if (useKeycloak) {
            AnonymousReadAccess.testAnonymousReadAccess(registry, "registry-admin", "changeme", true);

            KeycloakUtils.removeKeycloak();
        } else {
            AnonymousReadAccess.testAnonymousReadAccess(registry, null, null, false);
        }
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    protected void runBasicAuthenticationTest(
            ExtensionContext testContext,
            PersistenceKind persistenceKind,
            KafkaKind kafkaKind
    ) {
        ApicurioRegistry registry = deployTestRegistry(testContext, persistenceKind, kafkaKind, true);

        BasicAuthentication.testBasicAuthentication(registry, "registry-admin", "changeme");

        KeycloakUtils.removeKeycloak();
    }

    /* TESTS - PostgreSQL */

    @Test
    public void testRegistrySqlNoIAMAnonymousReadAccess(ExtensionContext testContext) {
        runAnonymousReadAccessTest(testContext, PersistenceKind.SQL, null, false);
    }

    @Test
    public void testRegistrySqlKeycloakAnonymousReadAccess(ExtensionContext testContext) {
        runAnonymousReadAccessTest(testContext, PersistenceKind.SQL, null, true);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    @Test
    public void testRegistrySqlKeycloakBasicAuthentication(ExtensionContext testContext) {
        runBasicAuthenticationTest(testContext, PersistenceKind.SQL, null);
    }

    /* TESTS - KafkaSQL */

    @Test
    public void testRegistryKafkasqlNoAuthNoIAMAnonymousReadAccess(ExtensionContext testContext) {
        runAnonymousReadAccessTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH, false);
    }

    @Test
    public void testRegistryKafkasqlNoAuthKeycloakAnonymousReadAccess(ExtensionContext testContext) {
        runAnonymousReadAccessTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH, true);
    }

    @Test
    public void testRegistryKafkasqlTLSNoIAMAnonymousReadAccess(ExtensionContext testContext) {
        runAnonymousReadAccessTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.TLS, false);
    }

    @Test
    public void testRegistryKafkasqlTLSKeycloakAnonymousReadAccess(ExtensionContext testContext) {
        runAnonymousReadAccessTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.TLS, true);
    }

    @Test
    public void testRegistryKafkasqlSCRAMNoIAMAnonymousReadAccess(ExtensionContext testContext) {
        runAnonymousReadAccessTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM, false);
    }

    @Test
    public void testRegistryKafkasqlSCRAMKeycloakAnonymousReadAccess(ExtensionContext testContext) {
        runAnonymousReadAccessTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM, true);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    @Test
    public void testRegistryKafkasqlNoAuthKeycloakBasicAuthentication(ExtensionContext testContext) {
        runBasicAuthenticationTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH);
    }

    @Test
    public void testRegistryKafkasqlTLSKeycloakBasicAuthentication(ExtensionContext testContext) {
        runBasicAuthenticationTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.TLS);
    }

    @Test
    public void testRegistryKafkasqlSCRAMKeycloakBasicAuthentication(ExtensionContext testContext) {
        runBasicAuthenticationTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM);
    }
}
