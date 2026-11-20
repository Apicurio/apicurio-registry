package io.apicurio.registry.auth.grants;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.Vertx;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

/**
 * Per-resource authorization of the MCP tool compatibility operations, on the root and v3 routes.
 * The caller must be able to read the source tool, and the candidates it cannot read are left out
 * of the results and of every count, so the response says nothing about them.
 */
@QuarkusTest
@TestProfile(GrantsMcpToolCompatibilityTest.McpToolCompatibilityProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class GrantsMcpToolCompatibilityTest extends AbstractResourceTestBase {

    private static final String GROUP = "mcp-tool-compatibility-grants";
    private static final String SOURCE = "records-source";
    private static final String VISIBLE = "visible-consumer";
    private static final String HIDDEN_COMPATIBLE = "hidden-consumer";
    private static final String HIDDEN_INCOMPATIBLE = "hidden-mismatch";

    private static final String ROOT_ROUTE = "/.well-known/mcp-tools/{groupId}/{artifactId}";
    private static final String V3_ROUTE = "/apis/registry/v3/well-known/mcp-tools/{groupId}/{artifactId}";
    private static final String COMPATIBLE = "/compatible";
    private static final String COMPATIBILITY = "/compatibility";

    private static final String RULES = """
            from io.apicurio.authz import Artifact;
            deny User with name = "bob" to READ Artifact with name like "%1$s/hidden-*";
            allow User with name = "bob" to READ Artifact with name like "%1$s/*";
            otherwise deny;
            """.formatted(GROUP);

    private static final String SOURCE_TOOL = """
            {
                "name": "records_source",
                "title": "Records Source",
                "description": "Produces records and their total",
                "inputSchema": { "type": "object" },
                "outputSchema": {
                    "type": "object",
                    "properties": {
                        "records": { "type": "array" },
                        "total": { "type": "integer" }
                    },
                    "required": ["records", "total"]
                }
            }
            """;

    private static final String VISIBLE_TOOL = """
            {
                "name": "visible_consumer",
                "title": "Visible Consumer",
                "description": "Consumes the records and total that the source produces",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "records": { "type": "array" },
                        "total": { "type": "integer" }
                    },
                    "required": ["records"]
                }
            }
            """;

    private static final String HIDDEN_COMPATIBLE_TOOL = """
            {
                "name": "hidden_consumer",
                "title": "hidden consumer",
                "description": "hidden from bob, accepts the records that the source produces",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "records": { "type": "array" },
                        "total": { "type": "integer" }
                    },
                    "required": ["records"]
                }
            }
            """;

    private static final String HIDDEN_INCOMPATIBLE_TOOL = """
            {
                "name": "hidden_mismatch",
                "title": "hidden mismatch",
                "description": "hidden from bob, expects records to be a string",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "records": { "type": "string" }
                    }
                }
            }
            """;

    static final Path RULES_FILE = createRulesFile();

    private static Path createRulesFile() {
        try {
            Path file = Files.createTempFile("grants-mcp-tool-compatibility", ".acl");
            Files.writeString(file, RULES);
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static class McpToolCompatibilityProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> map = new HashMap<>();
            map.put("apicurio.features.experimental.enabled", "true");
            map.put("apicurio.mcp-tools.enabled", "true");
            map.put("quarkus.oidc.tenant-enabled", "false");
            map.put("quarkus.http.auth.basic", "true");
            map.put("apicurio.auth.role-based-authorization", "true");
            map.put("quarkus.security.users.embedded.enabled", "true");
            map.put("quarkus.security.users.embedded.plain-text", "true");
            map.put("quarkus.security.users.embedded.users.alice", "alice");
            map.put("quarkus.security.users.embedded.users.bob", "bob");
            map.put("quarkus.security.users.embedded.roles.alice", "sr-admin");
            map.put("quarkus.security.users.embedded.roles.bob", "sr-developer");
            map.put("apicurio.auth.resource-based-authorization.enabled", "true");
            map.put("apicurio.auth.resource-based-authorization.rules.path", RULES_FILE.toString());
            map.put("apicurio.auth.resource-based-authorization.rules.reload-enabled", "false");
            return map;
        }
    }

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(registryV3ApiUrl).vertx(vertx).basicAuth("alice", "alice"));
    }

    @Override
    @BeforeAll
    protected void beforeAll() throws Exception {
        super.beforeAll();
        // Created by alice, so bob owns none of them and the rules alone decide what he can read
        createMcpTool(SOURCE, SOURCE_TOOL);
        createMcpTool(VISIBLE, VISIBLE_TOOL);
        createMcpTool(HIDDEN_COMPATIBLE, HIDDEN_COMPATIBLE_TOOL);
        createMcpTool(HIDDEN_INCOMPATIBLE, HIDDEN_INCOMPATIBLE_TOOL);
    }

    @ParameterizedTest
    @MethodSource("routes")
    public void missingCredentialsAreUnauthorized(String route) {
        given().baseUri(serverRootUrl()).pathParam("groupId", GROUP).pathParam("artifactId", SOURCE)
                .get(route).then().statusCode(401);
        as("bob", SOURCE).get(route).then().statusCode(200);
    }

    @ParameterizedTest
    @MethodSource("routes")
    public void sourceTheCallerCannotReadIsForbidden(String route) {
        as("bob", HIDDEN_COMPATIBLE).get(route).then().statusCode(403);
        as("alice", HIDDEN_COMPATIBLE).get(route).then().statusCode(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {ROOT_ROUTE + COMPATIBILITY, V3_ROUTE + COMPATIBILITY})
    public void compatibilityLeavesOutTheCandidatesTheCallerCannotRead(String route) {
        // bob can read the source and one candidate, and both are all that is counted
        as("bob", SOURCE).get(route).then().statusCode(200)
                .body("candidateTotal", equalTo(2))
                .body("evaluated", equalTo(1))
                .body("complete", equalTo(true))
                .body("totals.compatible", equalTo(1))
                .body("totals.incompatible", equalTo(0))
                .body("totals.indeterminate", equalTo(0))
                .body("count", equalTo(1))
                .body("results.tool.artifactId", contains(VISIBLE))
                .body("results[0].verdict", equalTo("COMPATIBLE"))
                .body(not(containsString("hidden")));

        // alice can read every tool, so she gets the hidden candidates and their reasons
        as("alice", SOURCE).queryParam("limit", 500).get(route).then().statusCode(200)
                .body(resultsInGroup() + ".tool.artifactId",
                        containsInAnyOrder(VISIBLE, HIDDEN_COMPATIBLE, HIDDEN_INCOMPATIBLE))
                .body(result(HIDDEN_COMPATIBLE) + ".verdict", equalTo("COMPATIBLE"))
                .body(result(HIDDEN_INCOMPATIBLE) + ".verdict", equalTo("INCOMPATIBLE"))
                .body(result(HIDDEN_INCOMPATIBLE) + ".reasons", hasSize(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {ROOT_ROUTE + COMPATIBLE, V3_ROUTE + COMPATIBLE})
    public void compatibleLeavesOutTheCandidatesTheCallerCannotRead(String route) {
        as("bob", SOURCE).get(route).then().statusCode(200)
                .body("count", equalTo(1))
                .body("tools.artifactId", contains(VISIBLE))
                .body(not(containsString("hidden")));

        as("alice", SOURCE).queryParam("limit", 500).get(route).then().statusCode(200)
                .body("tools.findAll { it.groupId == '" + GROUP + "' }.artifactId",
                        containsInAnyOrder(VISIBLE, HIDDEN_COMPATIBLE));
    }

    static List<String> routes() {
        return List.of(ROOT_ROUTE + COMPATIBLE, ROOT_ROUTE + COMPATIBILITY, V3_ROUTE + COMPATIBLE,
                V3_ROUTE + COMPATIBILITY);
    }

    private static RequestSpecification as(String user, String sourceArtifactId) {
        return given().baseUri(serverRootUrl()).auth().preemptive().basic(user, user)
                .pathParam("groupId", GROUP).pathParam("artifactId", sourceArtifactId);
    }

    private static String serverRootUrl() {
        int port = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
        return "http://localhost:" + port;
    }

    private static String resultsInGroup() {
        return "results.findAll { it.tool.groupId == '" + GROUP + "' }";
    }

    private static String result(String artifactId) {
        return "results.find { it.tool.groupId == '" + GROUP + "' && it.tool.artifactId == '" + artifactId + "' }";
    }

    private void createMcpTool(String artifactId, String content) {
        clientV3.groups().byGroupId(GROUP).artifacts().post(TestUtils.clientCreateArtifact(artifactId,
                ArtifactType.MCP_TOOL, content, ContentTypes.APPLICATION_JSON));
    }
}
