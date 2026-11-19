package io.apicurio.registry.agents.inventory;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.AgentInventorySource;
import io.apicurio.registry.rest.client.models.AgentInventorySyncRequest;
import io.apicurio.registry.rest.client.models.AgentInventorySyncResult;
import io.apicurio.registry.rest.client.models.AgentInventorySyncStatus;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
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
 * A sync writes artifacts, so it needs write access to the group; a read-only user is refused
 * before the inventory source is read.
 */
@QuarkusTest
@TestProfile(AgentInventoryAuthTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class AgentInventoryAuthTest extends AbstractResourceTestBase {

    private static final String LOCATION = "projects/auth-project/locations/europe-west1";

    @ConfigProperty(name = "quarkus.oidc.token-path")
    String authServerUrlConfigured;

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return clientFor(vertx, KeycloakTestContainerManager.ADMIN_CLIENT_ID);
    }

    private RegistryClient clientFor(Vertx vertx, String clientId) {
        return RegistryClientFactory.create(RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .oauth2(authServerUrlConfigured, clientId, "test1"));
    }

    private static AgentInventorySyncRequest request() {
        AgentInventorySyncRequest request = new AgentInventorySyncRequest();
        request.setSource(AgentInventorySource.GOOGLE_AGENT_REGISTRY);
        request.setLocation(LOCATION);
        return request;
    }

    private static void serveOneAgent() {
        GoogleAgentRegistryStub.get().reset(LOCATION);
        GoogleAgentRegistryStub.get().pages(LOCATION, "{\"agents\":[{\"name\":\"" + LOCATION
                + "/agents/support\",\"displayName\":\"Support\",\"description\":\"Answers tickets\","
                + "\"version\":\"1.0.0\",\"protocols\":[{\"type\":\"A2A_AGENT\",\"protocolVersion\":\"1.0\","
                + "\"interfaces\":[{\"url\":\"https://support.example.com/a2a\",\"protocolBinding\":\"JSONRPC\"}]}],"
                + "\"skills\":[{\"id\":\"answer\",\"name\":\"Answer\",\"description\":\"Answer\",\"tags\":[\"t\"]}]}]}");
    }

    @Test
    void readOnlyUserIsForbiddenAndTheSourceIsNotRead() {
        serveOneAgent();
        String group = TestUtils.generateGroupId();
        RegistryClient readOnly = clientFor(vertx, KeycloakTestContainerManager.READONLY_CLIENT_ID);

        Exception exception = Assertions.assertThrows(Exception.class,
                () -> readOnly.groups().byGroupId(group).agentInventorySync().post(request()));

        assertForbidden(exception);
        assertEquals(List.of(), GoogleAgentRegistryStub.get().authorizations(LOCATION));
    }

    @Test
    void developerSyncsIntoAGroup() {
        serveOneAgent();
        String group = TestUtils.generateGroupId();
        RegistryClient developer = clientFor(vertx, KeycloakTestContainerManager.DEVELOPER_CLIENT_ID);

        AgentInventorySyncResult result = developer.groups().byGroupId(group).agentInventorySync().post(request());

        assertEquals(1, result.getItems().size());
        assertEquals(AgentInventorySyncStatus.CREATED, result.getItems().get(0).getStatus());
        assertEquals("1", result.getItems().get(0).getVersion());
        assertEquals("GOOGLE_AGENT_REGISTRY", clientV3.groups().byGroupId(group).artifacts()
                .byArtifactId("gcp-agent.auth-project.europe-west1.support").get().getLabels()
                .getAdditionalData().get("apicurio.agent-inventory.source"));
    }
}
