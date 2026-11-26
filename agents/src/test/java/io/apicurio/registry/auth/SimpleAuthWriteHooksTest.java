package io.apicurio.registry.auth;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.AuthTestProfile;
import io.apicurio.registry.utils.tests.KeycloakTestContainerManager;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The agent write hooks (schema extraction from prompt templates) under the {@link SimpleAuthTest}
 * authentication setup.
 */
@QuarkusTest
@TestProfile(AuthTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class SimpleAuthWriteHooksTest extends AbstractResourceTestBase {

    @ConfigProperty(name = "quarkus.oidc.token-path")
    String authServerUrlConfigured;

    final String groupId = "authTestGroupId";

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return RegistryClientFactory.create(boundedClientOptions(registryV3ApiUrl, vertx)
                .oauth2(authServerUrlConfigured, KeycloakTestContainerManager.ADMIN_CLIENT_ID, "test1"));
    }

    @Test
    public void testOwnerOnlyAuthorizationIfExistsDoesNotRunWriteHooksForNonOwner() throws Exception {
        var clientDev = RegistryClientFactory.create(boundedClientOptions(registryV3ApiUrl, vertx)
                .oauth2(authServerUrlConfigured, KeycloakTestContainerManager.DEVELOPER_CLIENT_ID, "test1"));
        var clientAdmin = RegistryClientFactory.create(boundedClientOptions(registryV3ApiUrl, vertx)
                .oauth2(authServerUrlConfigured, KeycloakTestContainerManager.ADMIN_CLIENT_ID, "test1"));
        String artifactId = TestUtils.generateArtifactId();
        String schemaArtifactId = artifactId + "-output-schema";
        String promptV1 = """
                {"templateId": "t", "template": "Hello {{user}}",
                 "outputSchema": {"type": "object", "properties": {"greeting": {"type": "string"}}}}
                """;
        String promptV2 = promptV1.replace("\"greeting\"", "\"farewell\"");

        // Admin owns the prompt template; its embedded outputSchema becomes schema artifact version 1.
        clientAdmin.groups().byGroupId(groupId).artifacts().post(TestUtils.clientCreateArtifact(artifactId,
                ArtifactType.PROMPT_TEMPLATE, promptV1, ContentTypes.APPLICATION_JSON));
        assertEquals(1, clientAdmin.groups().byGroupId(groupId).artifacts().byArtifactId(schemaArtifactId)
                .versions().get().getCount());

        // Dev is not the owner: both ifExists modes are rejected before the schema-extraction hook runs.
        for (IfArtifactExists ifExists : List.of(IfArtifactExists.CREATE_VERSION,
                IfArtifactExists.FIND_OR_CREATE_VERSION)) {
            var exception = Assertions.assertThrows(Exception.class, () -> clientDev.groups().byGroupId(groupId)
                    .artifacts().post(TestUtils.clientCreateArtifact(artifactId, ArtifactType.PROMPT_TEMPLATE,
                            promptV2, ContentTypes.APPLICATION_JSON),
                            config -> config.queryParameters.ifExists = ifExists));
            assertForbidden(exception);
        }
        assertEquals(1, clientAdmin.groups().byGroupId(groupId).artifacts().byArtifactId(schemaArtifactId)
                .versions().get().getCount());
        assertEquals(1, clientAdmin.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().get().getCount());

        // The owner can, and the changed schema is extracted into a new schema version.
        var created = clientAdmin.groups().byGroupId(groupId).artifacts().post(TestUtils.clientCreateArtifact(
                artifactId, ArtifactType.PROMPT_TEMPLATE, promptV2, ContentTypes.APPLICATION_JSON),
                config -> config.queryParameters.ifExists = IfArtifactExists.CREATE_VERSION);
        assertEquals("2", created.getVersion().getVersion());
        assertEquals(2, clientAdmin.groups().byGroupId(groupId).artifacts().byArtifactId(schemaArtifactId)
                .versions().get().getCount());
    }
}
