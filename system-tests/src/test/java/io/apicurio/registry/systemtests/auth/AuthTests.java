package io.apicurio.registry.systemtests.auth;

import io.apicur.registry.v1.ApicurioRegistry;
import io.apicurio.registry.systemtests.TestBase;
import io.apicurio.registry.systemtests.auth.features.AnonymousReadAccess;
import io.apicurio.registry.systemtests.auth.features.ArtifactGroupOwnerOnlyAuthorization;
import io.apicurio.registry.systemtests.auth.features.ArtifactOwnerOnlyAuthorization;
import io.apicurio.registry.systemtests.auth.features.AuthenticatedReads;
import io.apicurio.registry.systemtests.auth.features.BasicAuthentication;
import io.apicurio.registry.systemtests.auth.features.RoleBasedAuthorizationAdminOverrideRole;
import io.apicurio.registry.systemtests.auth.features.RoleBasedAuthorizationApplication;
import io.apicurio.registry.systemtests.auth.features.RoleBasedAuthorizationRoleNames;
import io.apicurio.registry.systemtests.auth.features.RoleBasedAuthorizationToken;
import io.apicurio.registry.systemtests.framework.Constants;
import io.apicurio.registry.systemtests.registryinfra.resources.KafkaKind;
import io.apicurio.registry.systemtests.registryinfra.resources.PersistenceKind;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;

@Disabled
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AuthTests extends TestBase {
    /* TEST RUNNERS */

    protected void runAnonymousReadAccessTest(
            ExtensionContext testContext,
            PersistenceKind persistenceKind,
            KafkaKind kafkaKind,
            boolean useKeycloak
    ) throws InterruptedException {
        ApicurioRegistry registry = deployTestRegistry(testContext, persistenceKind, kafkaKind, useKeycloak);

        if (useKeycloak) {
            AnonymousReadAccess.testAnonymousReadAccess(
                    registry,
                    Constants.SSO_ADMIN_USER,
                    Constants.SSO_USER_PASSWORD,
                    true
            );
        } else {
            AnonymousReadAccess.testAnonymousReadAccess(registry, null, null, false);
        }
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    protected void runBasicAuthenticationTest(
            ExtensionContext testContext,
            PersistenceKind persistenceKind,
            KafkaKind kafkaKind
    ) throws InterruptedException {
        ApicurioRegistry registry = deployTestRegistry(testContext, persistenceKind, kafkaKind, true);

        BasicAuthentication.testBasicAuthentication(registry, Constants.SSO_ADMIN_USER, Constants.SSO_USER_PASSWORD);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    protected void runAuthenticatedReadsTest(
            ExtensionContext testContext,
            PersistenceKind persistenceKind,
            KafkaKind kafkaKind
    ) throws InterruptedException {
        ApicurioRegistry registry = deployTestRegistry(testContext, persistenceKind, kafkaKind, true);

        AuthenticatedReads.testAuthenticatedReads(registry);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    protected void runArtifactOwnerOnlyAuthorizationTest(
            ExtensionContext testContext,
            PersistenceKind persistenceKind,
            KafkaKind kafkaKind
    ) throws InterruptedException {
        ApicurioRegistry registry = deployTestRegistry(testContext, persistenceKind, kafkaKind, true);

        ArtifactOwnerOnlyAuthorization.testArtifactOwnerOnlyAuthorization(registry);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    protected void runArtifactGroupOwnerOnlyAuthorizationTest(
            ExtensionContext testContext,
            PersistenceKind persistenceKind,
            KafkaKind kafkaKind
    ) throws InterruptedException {
        ApicurioRegistry registry = deployTestRegistry(testContext, persistenceKind, kafkaKind, true);

        ArtifactGroupOwnerOnlyAuthorization.testArtifactGroupOwnerOnlyAuthorization(registry);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    protected void runRoleBasedAuthorizationTokenTest(
            ExtensionContext testContext,
            PersistenceKind persistenceKind,
            KafkaKind kafkaKind
    ) throws InterruptedException {
        ApicurioRegistry registry = deployTestRegistry(testContext, persistenceKind, kafkaKind, true);

        RoleBasedAuthorizationToken.testRoleBasedAuthorizationToken(registry);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    protected void runRoleBasedAuthorizationApplicationTest(
            ExtensionContext testContext,
            PersistenceKind persistenceKind,
            KafkaKind kafkaKind
    ) throws InterruptedException {
        ApicurioRegistry registry = deployTestRegistry(testContext, persistenceKind, kafkaKind, true);

        RoleBasedAuthorizationApplication.testRoleBasedAuthorizationApplication(registry);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    protected void runRoleBasedAuthorizationRoleNamesTest(
            ExtensionContext testContext,
            PersistenceKind persistenceKind,
            KafkaKind kafkaKind
    ) throws InterruptedException {
        ApicurioRegistry registry = deployTestRegistry(testContext, persistenceKind, kafkaKind, true);

        RoleBasedAuthorizationRoleNames.testRoleBasedAuthorizationRoleNames(registry);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    protected void runRoleBasedAuthorizationAdminOverrideRoleTest(
            ExtensionContext testContext,
            PersistenceKind persistenceKind,
            KafkaKind kafkaKind
    ) throws InterruptedException, IOException {
        ApicurioRegistry registry = deployTestRegistry(testContext, persistenceKind, kafkaKind, true);

        RoleBasedAuthorizationAdminOverrideRole.testRoleBasedAuthorizationAdminOverrideRole(registry);
    }

    /* TESTS - PostgreSQL */

    @Test
    public void testRegistrySqlNoIAMAnonymousReadAccess(ExtensionContext testContext) throws InterruptedException {
        runAnonymousReadAccessTest(testContext, PersistenceKind.SQL, null, false);
    }

    @Test
    public void testRegistrySqlKeycloakAnonymousReadAccess(ExtensionContext testContext) throws InterruptedException {
        runAnonymousReadAccessTest(testContext, PersistenceKind.SQL, null, true);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    @Test
    public void testRegistrySqlKeycloakBasicAuthentication(ExtensionContext testContext) throws InterruptedException {
        runBasicAuthenticationTest(testContext, PersistenceKind.SQL, null);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    @Test
    public void testRegistrySqlKeycloakAuthenticatedReads(ExtensionContext testContext) throws InterruptedException {
        runAuthenticatedReadsTest(testContext, PersistenceKind.SQL, null);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    @Test
    public void testRegistrySqlKeycloakArtifactOwnerOnlyAuthorization(
            ExtensionContext testContext
    ) throws InterruptedException {
        runArtifactOwnerOnlyAuthorizationTest(testContext, PersistenceKind.SQL, null);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    @Test
    public void testRegistrySqlKeycloakArtifactGroupOwnerOnlyAuthorization(
            ExtensionContext testContext
    ) throws InterruptedException {
        runArtifactGroupOwnerOnlyAuthorizationTest(testContext, PersistenceKind.SQL, null);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    @Test
    public void testRegistrySqlKeycloakRoleBasedAuthorizationToken(
            ExtensionContext testContext
    ) throws InterruptedException {
        runRoleBasedAuthorizationTokenTest(testContext, PersistenceKind.SQL, null);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    @Test
    public void testRegistrySqlKeycloakRoleBasedAuthorizationApplication(
            ExtensionContext testContext
    ) throws InterruptedException {
        runRoleBasedAuthorizationApplicationTest(testContext, PersistenceKind.SQL, null);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    @Test
    public void testRegistrySqlKeycloakRoleBasedAuthorizationRoleNames(
            ExtensionContext testContext
    ) throws InterruptedException {
        runRoleBasedAuthorizationRoleNamesTest(testContext, PersistenceKind.SQL, null);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    @Test
    public void testRegistrySqlKeycloakRoleBasedAuthorizationAdminOverrideRole(
            ExtensionContext testContext
    ) throws InterruptedException, IOException {
        runRoleBasedAuthorizationAdminOverrideRoleTest(testContext, PersistenceKind.SQL, null);
    }

    /* TESTS - KafkaSQL */

    @Test
    public void testRegistryKafkasqlNoAuthNoIAMAnonymousReadAccess(
            ExtensionContext testContext
    ) throws InterruptedException {
        runAnonymousReadAccessTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH, false);
    }

    @Test
    public void testRegistryKafkasqlNoAuthKeycloakAnonymousReadAccess(
            ExtensionContext testContext
    ) throws InterruptedException {
        runAnonymousReadAccessTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH, true);
    }

    @Test
    public void testRegistryKafkasqlTLSNoIAMAnonymousReadAccess(
            ExtensionContext testContext
    ) throws InterruptedException {
        runAnonymousReadAccessTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.TLS, false);
    }

    @Test
    public void testRegistryKafkasqlTLSKeycloakAnonymousReadAccess(
            ExtensionContext testContext
    ) throws InterruptedException {
        runAnonymousReadAccessTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.TLS, true);
    }

    @Test
    public void testRegistryKafkasqlSCRAMNoIAMAnonymousReadAccess(
            ExtensionContext testContext
    ) throws InterruptedException {
        runAnonymousReadAccessTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM, false);
    }

    @Test
    public void testRegistryKafkasqlSCRAMKeycloakAnonymousReadAccess(
            ExtensionContext testContext
    ) throws InterruptedException {
        runAnonymousReadAccessTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM, true);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    @Test
    public void testRegistryKafkasqlNoAuthKeycloakBasicAuthentication(
            ExtensionContext testContext
    ) throws InterruptedException {
        runBasicAuthenticationTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH);
    }

    @Test
    public void testRegistryKafkasqlTLSKeycloakBasicAuthentication(
            ExtensionContext testContext
    ) throws InterruptedException {
        runBasicAuthenticationTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.TLS);
    }

    @Test
    public void testRegistryKafkasqlSCRAMKeycloakBasicAuthentication(
            ExtensionContext testContext
    ) throws InterruptedException {
        runBasicAuthenticationTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    @Test
    public void testRegistryKafkasqlNoAuthKeycloakAuthenticatedReads(
            ExtensionContext testContext
    ) throws InterruptedException {
        runAuthenticatedReadsTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH);
    }

    @Test
    public void testRegistryKafkasqlTLSKeycloakAuthenticatedReads(
            ExtensionContext testContext
    ) throws InterruptedException {
        runAuthenticatedReadsTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.TLS);
    }

    @Test
    public void testRegistryKafkasqlSCRAMKeycloakAuthenticatedReads(
            ExtensionContext testContext
    ) throws InterruptedException {
        runAuthenticatedReadsTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    @Test
    public void testRegistryKafkasqlNoAuthKeycloakArtifactOwnerOnlyAuthorization(
            ExtensionContext testContext
    ) throws InterruptedException {
        runArtifactOwnerOnlyAuthorizationTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH);
    }

    @Test
    public void testRegistryKafkasqlTLSKeycloakArtifactOwnerOnlyAuthorization(
            ExtensionContext testContext
    ) throws InterruptedException {
        runArtifactOwnerOnlyAuthorizationTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.TLS);
    }

    @Test
    public void testRegistryKafkasqlSCRAMKeycloakArtifactOwnerOnlyAuthorization(
            ExtensionContext testContext
    ) throws InterruptedException {
        runArtifactOwnerOnlyAuthorizationTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    @Test
    public void testRegistryKafkasqlNoAuthKeycloakArtifactGroupOwnerOnlyAuthorization(
            ExtensionContext testContext
    ) throws InterruptedException {
        runArtifactGroupOwnerOnlyAuthorizationTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH);
    }

    @Test
    public void testRegistryKafkasqlTLSKeycloakArtifactGroupOwnerOnlyAuthorization(
            ExtensionContext testContext
    ) throws InterruptedException {
        runArtifactGroupOwnerOnlyAuthorizationTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.TLS);
    }

    @Test
    public void testRegistryKafkasqlSCRAMKeycloakArtifactGroupOwnerOnlyAuthorization(
            ExtensionContext testContext
    ) throws InterruptedException {
        runArtifactGroupOwnerOnlyAuthorizationTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    @Test
    public void testRegistryKafkasqlNoAuthKeycloakRoleBasedAuthorizationToken(
            ExtensionContext testContext
    ) throws InterruptedException {
        runRoleBasedAuthorizationTokenTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH);
    }

    @Test
    public void testRegistryKafkasqlTLSKeycloakRoleBasedAuthorizationToken(
            ExtensionContext testContext
    ) throws InterruptedException {
        runRoleBasedAuthorizationTokenTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.TLS);
    }

    @Test
    public void testRegistryKafkasqlSCRAMKeycloakRoleBasedAuthorizationToken(
            ExtensionContext testContext
    ) throws InterruptedException {
        runRoleBasedAuthorizationTokenTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    @Test
    public void testRegistryKafkasqlNoAuthKeycloakRoleBasedAuthorizationApplication(
            ExtensionContext testContext
    ) throws InterruptedException {
        runRoleBasedAuthorizationApplicationTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH);
    }

    @Test
    public void testRegistryKafkasqlTLSKeycloakRoleBasedAuthorizationApplication(
            ExtensionContext testContext
    ) throws InterruptedException {
        runRoleBasedAuthorizationApplicationTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.TLS);
    }

    @Test
    public void testRegistryKafkasqlSCRAMKeycloakRoleBasedAuthorizationApplication(
            ExtensionContext testContext
    ) throws InterruptedException {
        runRoleBasedAuthorizationApplicationTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    @Test
    public void testRegistryKafkasqlNoAuthKeycloakRoleBasedAuthorizationRoleNames(
            ExtensionContext testContext
    ) throws InterruptedException {
        runRoleBasedAuthorizationRoleNamesTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH);
    }

    @Test
    public void testRegistryKafkasqlTLSKeycloakRoleBasedAuthorizationRoleNames(
            ExtensionContext testContext
    ) throws InterruptedException {
        runRoleBasedAuthorizationRoleNamesTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.TLS);
    }

    @Test
    public void testRegistryKafkasqlSCRAMKeycloakRoleBasedAuthorizationRoleNames(
            ExtensionContext testContext
    ) throws InterruptedException {
        runRoleBasedAuthorizationRoleNamesTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM);
    }
    /* -------------------------------------------------------------------------------------------------------------- */
    @Test
    public void testRegistryKafkasqlNoAuthKeycloakRoleBasedAuthorizationAdminOverrideRole(
            ExtensionContext testContext
    ) throws InterruptedException, IOException {
        runRoleBasedAuthorizationAdminOverrideRoleTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH);
    }

    @Test
    public void testRegistryKafkasqlTLSKeycloakRoleBasedAuthorizationAdminOverrideRole(
            ExtensionContext testContext
    ) throws InterruptedException, IOException {
        runRoleBasedAuthorizationAdminOverrideRoleTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.TLS);
    }

    @Test
    public void testRegistryKafkasqlSCRAMKeycloakRoleBasedAuthorizationAdminOverrideRole(
            ExtensionContext testContext
    ) throws InterruptedException, IOException {
        runRoleBasedAuthorizationAdminOverrideRoleTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM);
    }
}
