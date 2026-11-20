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
import io.quarkus.test.junit.TestProfile;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.Vertx;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

/**
 * MCP tool discovery with per-resource grants: listing and fetching tools by ID. The compatibility
 * endpoints are covered by {@link GrantsMcpToolCompatibilityTest}, whose profile and rules this test
 * shares: bob can read the group's artifacts except those named {@code hidden-*}.
 */
@QuarkusTest
@TestProfile(GrantsMcpToolCompatibilityTest.McpToolCompatibilityProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class GrantsMcpToolDiscoveryTest extends AbstractResourceTestBase {

    private static final String GROUP = "mcp-tool-compatibility-grants";
    private static final String VISIBLE = "discovery-visible";
    private static final String HIDDEN = "hidden-discovery";

    private static final String ROOT = "/.well-known/mcp-tools";
    private static final String V3 = "/apis/registry/v3/well-known/mcp-tools";

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(registryV3ApiUrl).vertx(vertx).basicAuth("alice", "alice"));
    }

    @Override
    @BeforeAll
    protected void beforeAll() throws Exception {
        super.beforeAll();
        // Created by alice, so bob owns neither and the rules alone decide what he can read
        for (String artifactId : List.of(VISIBLE, HIDDEN)) {
            String tool = "{\"name\": \"grants_" + artifactId.replace('-', '_')
                    + "\", \"description\": \"Grants discovery tool\", \"inputSchema\": {\"type\": \"object\"}}";
            clientV3.groups().byGroupId(GROUP).artifacts().post(TestUtils.clientCreateArtifact(artifactId,
                    ArtifactType.MCP_TOOL, tool, ContentTypes.APPLICATION_JSON));
        }
    }

    private static String serverRootUrl() {
        return "http://localhost:" + ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
    }

    private static RequestSpecification as(String user) {
        return given().baseUri(serverRootUrl()).basePath("").auth().preemptive().basic(user, user);
    }

    private static List<String> listed(RequestSpecification spec, String route) {
        JsonPath body = spec.queryParam("name", "discovery").queryParam("limit", 100).get(route)
                .then().statusCode(200).extract().jsonPath();
        List<String> ids = body.getList("tools.findAll { it.groupId == '" + GROUP + "' }.artifactId",
                String.class);
        // The count must not reveal tools the caller cannot read
        assertThat(body.getInt("count"), equalTo(body.getList("tools").size()));
        return ids;
    }

    @ParameterizedTest
    @ValueSource(strings = {ROOT, V3})
    void listingLeavesOutToolsTheCallerCannotRead(String route) {
        List<String> bob = listed(as("bob"), route);
        assertThat(bob, hasItem(VISIBLE));
        assertThat(bob, not(hasItem(HIDDEN)));
        List<String> alice = listed(as("alice"), route);
        assertThat(alice, hasItem(VISIBLE));
        assertThat(alice, hasItem(HIDDEN));
    }

    @ParameterizedTest
    @ValueSource(strings = {ROOT, V3})
    void fetchingByIdRequiresReadAccess(String route) {
        as("bob").get(route + "/" + GROUP + "/" + VISIBLE).then().statusCode(200)
                .body("name", equalTo("grants_discovery_visible"));
        as("bob").get(route + "/" + GROUP + "/" + HIDDEN).then().statusCode(403);
        as("alice").get(route + "/" + GROUP + "/" + HIDDEN).then().statusCode(200)
                .body("name", equalTo("grants_hidden_discovery"));
    }

    @ParameterizedTest
    @ValueSource(strings = {ROOT, V3})
    void missingCredentialsAreUnauthorized(String route) {
        given().baseUri(serverRootUrl()).basePath("").get(route).then().statusCode(401);
        given().baseUri(serverRootUrl()).basePath("").get(route + "/" + GROUP + "/" + VISIBLE)
                .then().statusCode(401);
    }
}
