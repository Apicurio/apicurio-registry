package io.apicurio.registry.agents.mcpimport;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.McpToolImportJobState;
import io.apicurio.registry.rest.client.models.McpToolImportJobStatus;
import io.apicurio.registry.rest.client.models.McpToolImportRequest;
import io.apicurio.registry.rest.client.models.McpToolImportResult;
import io.apicurio.registry.rest.client.models.McpToolImportStatus;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.KeycloakTestContainerManager;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Importing writes artifacts, so it needs write access; a read-only user is refused before the
 * registry contacts the MCP server.
 */
@QuarkusTest
@TestProfile(McpImportAuthTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(McpImportTest.LOCK)
public class McpImportAuthTest extends AbstractResourceTestBase {

    McpTestServer mcp;

    @ConfigProperty(name = "quarkus.oidc.token-path")
    String authServerUrlConfigured;

    @Inject
    McpToolImportService importService;

    @Inject
    McpImportConfig importConfig;

    @BeforeAll
    void startMcp() throws Exception {
        mcp = new McpTestServer();
        mcp.tools(McpTestServer.tool("search", "string"));
    }

    @AfterAll
    void stopMcp() {
        if (mcp != null) {
            mcp.close();
        }
    }

    @AfterEach
    void awaitBackgroundImports() {
        await().atMost(Duration.ofSeconds(30))
                .until(() -> importService.availablePermits() == importConfig.getMaxConcurrent());
    }

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return clientFor(vertx, KeycloakTestContainerManager.ADMIN_CLIENT_ID);
    }

    private RegistryClient clientFor(Vertx vertx, String clientId) {
        return RegistryClientFactory.create(boundedClientOptions(registryV3ApiUrl, vertx)
                .oauth2(authServerUrlConfigured, clientId, "test1"));
    }

    private String serverArtifact() throws Exception {
        String group = TestUtils.generateGroupId();
        createArtifact(group, "srv", ArtifactType.MCP_SERVER,
                "{\"name\":\"io.github.test/srv\",\"description\":\"A server\",\"version\":\"1.0.0\"}",
                ContentTypes.APPLICATION_JSON);
        return group;
    }

    private McpToolImportRequest request() {
        McpToolImportRequest request = new McpToolImportRequest();
        request.setUrl(mcp.url());
        return request;
    }

    @Test
    void readOnlyUserIsForbiddenAndTheServerIsNotContacted() throws Exception {
        String group = serverArtifact();
        int before = mcp.requests.get();
        RegistryClient readOnly = clientFor(vertx, KeycloakTestContainerManager.READONLY_CLIENT_ID);

        Exception exception = Assertions.assertThrows(Exception.class, () -> readOnly.groups().byGroupId(group)
                .artifacts().byArtifactId("srv").mcpImport().post(request()));

        assertForbidden(exception);
        assertEquals(before, mcp.requests.get());
        ProblemDetails missing = Assertions.assertThrows(ProblemDetails.class,
                () -> clientV3.groups().byGroupId(group).artifacts().byArtifactId("srv.search").get());
        assertEquals(404, missing.getResponseStatusCode());
    }

    @Test
    void developerImportsIntoTheirOwnServerArtifact() throws Exception {
        RegistryClient developer = clientFor(vertx, KeycloakTestContainerManager.DEVELOPER_CLIENT_ID);
        String group = developerServerArtifact(developer);

        McpToolImportResult result = developer.groups().byGroupId(group).artifacts().byArtifactId("srv")
                .mcpImport().post(request());

        assertEquals(McpToolImportStatus.NEW_VERSION, result.getStatus());
        assertEquals(McpToolImportStatus.CREATED, result.getTools().get(0).getStatus());
        assertEquals("srv.search", clientV3.groups().byGroupId(group).artifacts().byArtifactId("srv.search").get()
                .getArtifactId());
    }

    private String developerServerArtifact(RegistryClient developer) {
        String group = TestUtils.generateGroupId();
        CreateArtifact server = new CreateArtifact();
        server.setArtifactId("srv");
        server.setArtifactType(ArtifactType.MCP_SERVER);
        CreateVersion first = new CreateVersion();
        VersionContent content = new VersionContent();
        content.setContent("{\"name\":\"io.github.test/srv\",\"description\":\"A server\",\"version\":\"1.0.0\"}");
        content.setContentType(ContentTypes.APPLICATION_JSON);
        first.setContent(content);
        server.setFirstVersion(first);
        developer.groups().byGroupId(group).artifacts().post(server);
        return group;
    }

    /**
     * Owner-only authorization applies to the tool artifacts as well. Access to existing tool
     * artifacts is checked before anything is written, so a refused import writes no tool at all,
     * not even the ones listed before the refused one.
     */
    @Test
    void toolArtifactOwnedBySomeoneElseRefusesTheWholeImport() throws Exception {
        RegistryClient developer = clientFor(vertx, KeycloakTestContainerManager.DEVELOPER_CLIENT_ID);
        String group = developerServerArtifact(developer);
        createArtifact(group, "srv.search", ArtifactType.MCP_TOOL,
                McpTestServer.tool("search", "string").encode(), ContentTypes.APPLICATION_JSON);
        mcp.tools(McpTestServer.tool("fetch", "string"), McpTestServer.tool("search", "integer"));
        try {
            Exception exception = Assertions.assertThrows(Exception.class, () -> developer.groups()
                    .byGroupId(group).artifacts().byArtifactId("srv").mcpImport().post(request()));

            assertForbidden(exception);
            ProblemDetails missing = Assertions.assertThrows(ProblemDetails.class,
                    () -> clientV3.groups().byGroupId(group).artifacts().byArtifactId("srv.fetch").get());
            assertEquals(404, missing.getResponseStatusCode());
            assertEquals(1, clientV3.groups().byGroupId(group).artifacts().byArtifactId("srv.search").versions()
                    .get().getCount());
            assertEquals(1, clientV3.groups().byGroupId(group).artifacts().byArtifactId("srv").versions()
                    .get().getCount());
        } finally {
            mcp.tools(McpTestServer.tool("search", "string"));
        }
    }

    /**
     * The test realm enables owner-only authorization: a developer may not add versions to an
     * artifact someone else created, so it cannot import into it either.
     */
    @Test
    void developerCannotImportIntoAServerArtifactOwnedBySomeoneElse() throws Exception {
        String group = serverArtifact();
        int before = mcp.requests.get();
        RegistryClient developer = clientFor(vertx, KeycloakTestContainerManager.DEVELOPER_CLIENT_ID);

        Exception exception = Assertions.assertThrows(Exception.class, () -> developer.groups().byGroupId(group)
                .artifacts().byArtifactId("srv").mcpImport().post(request()));

        assertForbidden(exception);
        assertEquals(before, mcp.requests.get());
    }

    private McpToolImportRequest asyncRequest() {
        McpToolImportRequest request = request();
        request.setAsync(true);
        return request;
    }

    private McpToolImportJobStatus awaitFinished(RegistryClient client, String group) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertNotEquals(McpToolImportJobState.RUNNING, client.groups().byGroupId(group)
                        .artifacts().byArtifactId("srv").mcpImport().status().get().getState()));
        return client.groups().byGroupId(group).artifacts().byArtifactId("srv").mcpImport().status().get();
    }

    /**
     * The background import writes as the caller who started it: its tools are owned by them, as
     * they would be after a synchronous import.
     */
    @Test
    void asyncImportWritesAsTheCaller() throws Exception {
        RegistryClient developer = clientFor(vertx, KeycloakTestContainerManager.DEVELOPER_CLIENT_ID);
        String group = developerServerArtifact(developer);

        developer.groups().byGroupId(group).artifacts().byArtifactId("srv").mcpImport().post(asyncRequest());

        McpToolImportJobStatus status = awaitFinished(developer, group);
        assertEquals(McpToolImportJobState.COMPLETED, status.getState());
        assertEquals("2", status.getVersion());
        assertEquals(1, ((Number) status.getCounts().getAdditionalData().get("CREATED")).intValue());
        String developerOwner = clientV3.groups().byGroupId(group).artifacts().byArtifactId("srv").get().getOwner();
        String adminOwner = clientV3.groups().byGroupId(serverArtifact()).artifacts().byArtifactId("srv").get()
                .getOwner();
        assertNotEquals(adminOwner, developerOwner);
        assertEquals(developerOwner, clientV3.groups().byGroupId(group).artifacts().byArtifactId("srv.search").get()
                .getOwner());
        assertEquals(developerOwner, clientV3.groups().byGroupId(group).artifacts().byArtifactId("srv").versions()
                .byVersionExpression("2").get().getOwner());
    }

    @Test
    void readOnlyUserCanReadTheStatusButNotStartAnAsyncImport() throws Exception {
        String group = serverArtifact();
        RegistryClient readOnly = clientFor(vertx, KeycloakTestContainerManager.READONLY_CLIENT_ID);
        int before = mcp.requests.get();

        Exception exception = Assertions.assertThrows(Exception.class, () -> readOnly.groups().byGroupId(group)
                .artifacts().byArtifactId("srv").mcpImport().post(asyncRequest()));
        assertForbidden(exception);
        assertEquals(before, mcp.requests.get());
        ProblemDetails none = Assertions.assertThrows(ProblemDetails.class, () -> readOnly.groups()
                .byGroupId(group).artifacts().byArtifactId("srv").mcpImport().status().get());
        assertEquals(404, none.getResponseStatusCode());

        clientV3.groups().byGroupId(group).artifacts().byArtifactId("srv").mcpImport().post(asyncRequest());
        assertEquals(McpToolImportJobState.COMPLETED, awaitFinished(clientV3, group).getState());

        McpToolImportJobStatus status = readOnly.groups().byGroupId(group).artifacts().byArtifactId("srv")
                .mcpImport().status().get();
        assertEquals(McpToolImportJobState.COMPLETED, status.getState());
        assertEquals("2", status.getVersion());
    }
}
