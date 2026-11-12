package io.apicurio.registry.auth.grants;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.Vertx;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;

/**
 * Agent discovery with per-resource grants enabled: an agent appears in {@code /.well-known/agents}
 * and in ARD search only for callers whose grants allow reading its group, whatever its visibility
 * label says. Anonymous read access is on, so without grants anonymous callers would see the
 * public agents.
 */
@QuarkusTest
@TestProfile(GrantsAgentDiscoveryTest.AgentDiscoveryGrantsProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class GrantsAgentDiscoveryTest extends AbstractResourceTestBase {

    private static final String OPEN_GROUP = "agents-open";
    private static final String LOCKED_GROUP = "agents-locked";

    private static final String RULES = """
            from io.apicurio.authz import Artifact, Group;
            allow User with name = "bob" to READ Group with name = "%1$s";
            allow User with name = "bob" to READ Artifact with name like "%1$s/*";
            otherwise deny;
            """.formatted(OPEN_GROUP);

    static final Path GRANTS = createGrantsFile();

    private static Path createGrantsFile() {
        try {
            Path file = Files.createTempFile("grants-agent-discovery", ".acl");
            Files.writeString(file, RULES);
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static class AgentDiscoveryGrantsProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> map = new HashMap<>();
            map.put("quarkus.oidc.tenant-enabled", "false");
            map.put("quarkus.http.auth.basic", "true");
            map.put("apicurio.auth.role-based-authorization", "true");
            map.put("quarkus.security.users.embedded.enabled", "true");
            map.put("quarkus.security.users.embedded.plain-text", "true");
            map.put("quarkus.security.users.embedded.users.alice", "alice");
            map.put("quarkus.security.users.embedded.users.bob", "bob");
            map.put("quarkus.security.users.embedded.users.carol", "carol");
            map.put("quarkus.security.users.embedded.roles.alice", "sr-admin");
            map.put("quarkus.security.users.embedded.roles.bob", "sr-developer");
            map.put("quarkus.security.users.embedded.roles.carol", "sr-developer");
            // Anonymous callers could otherwise discover public agents
            map.put("apicurio.auth.anonymous-read-access.enabled", "true");
            map.put("apicurio.auth.resource-based-authorization.enabled", "true");
            map.put("apicurio.auth.resource-based-authorization.rules.path", GRANTS.toString());
            map.put("apicurio.auth.resource-based-authorization.rules.reload-enabled", "false");
            map.put("apicurio.auth.resource-based-authorization.owner-bypass.enabled", "false");
            map.put("apicurio.features.experimental.enabled", "true");
            map.put("apicurio.a2a.enabled", "true");
            map.put("apicurio.ai-catalog.enabled", "true");
            map.put("apicurio.ard.enabled", "true");
            return map;
        }
    }

    private String serverRootUrl;

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(registryV3ApiUrl).vertx(vertx).basicAuth("alice", "alice"));
    }

    @BeforeEach
    void createAgents() {
        serverRootUrl = "http://localhost"
                + ":" + ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
        for (String group : List.of(OPEN_GROUP, LOCKED_GROUP)) {
            for (String visibility : List.of("public", "entitled")) {
                createAgent(group, visibility);
            }
        }
    }

    private void createAgent(String group, String visibility) {
        String artifactId = visibility + "-agent";
        if (as("alice").get("/registry/v3/groups/" + group + "/artifacts/" + artifactId).statusCode() == 200) {
            return;
        }
        String card = """
                {"name": "%1$s %2$s", "description": "An agent", "version": "1.0.0",
                 "supportedInterfaces": [{"url": "https://example.com/%1$s/%2$s",
                   "protocolBinding": "JSONRPC", "protocolVersion": "1.0"}],
                 "capabilities": {}, "skills": [], "defaultInputModes": ["text"],
                 "defaultOutputModes": ["text"]}
                """.formatted(group, visibility);
        as("alice").contentType(CT_JSON)
                .body(Map.of("artifactId", artifactId, "artifactType", "AGENT_CARD",
                        "labels", Map.of("apicurio.agent.visibility", visibility),
                        "firstVersion", Map.of("version", "1",
                                "content", Map.of("content", card, "contentType", CT_JSON))))
                .post("/registry/v3/groups/" + group + "/artifacts").then().statusCode(200);
    }

    private static RequestSpecification as(String user) {
        return given().auth().preemptive().basic(user, user);
    }

    private RequestSpecification atRoot(RequestSpecification spec) {
        return spec.baseUri(serverRootUrl).basePath("");
    }

    private List<String> wellKnownAgents(RequestSpecification spec) {
        JsonPath body = atRoot(spec).get("/.well-known/agents?limit=100").then().statusCode(200)
                .extract().jsonPath();
        List<String> groups = body.getList("agents.groupId", String.class);
        List<String> artifacts = body.getList("agents.artifactId", String.class);
        return IntStream.range(0, groups.size()).mapToObj(i -> groups.get(i) + "/" + artifacts.get(i))
                .filter(agent -> agent.startsWith("agents-")).toList();
    }

    private List<String> ardSearch(RequestSpecification spec) {
        return atRoot(spec).contentType(ContentType.JSON)
                .body("{\"query\": {\"text\": \"-agent\"}}")
                .post("/.well-known/ard/search").then().statusCode(200)
                .extract().jsonPath().getList("results.identifier", String.class).stream()
                .filter(id -> id.contains(":agents-"))
                .map(id -> id.substring(id.indexOf(":agents-") + 1).replace(':', '/'))
                .toList();
    }

    @Test
    void grantedUserDiscoversOnlyAgentsInTheGrantedGroup() {
        List<String> expected = List.of(OPEN_GROUP + "/public-agent", OPEN_GROUP + "/entitled-agent");
        assertThat(wellKnownAgents(as("bob")), containsInAnyOrder(expected.toArray()));
        assertThat(ardSearch(as("bob")), containsInAnyOrder(expected.toArray()));
    }

    @Test
    void developerWithoutGrantsDiscoversNothingAndCannotReadTheGroup() {
        assertThat(wellKnownAgents(as("carol")), empty());
        assertThat(ardSearch(as("carol")), empty());
        as("carol").get("/registry/v3/groups/" + OPEN_GROUP + "/artifacts/entitled-agent")
                .then().statusCode(403);
        as("bob").get("/registry/v3/groups/" + LOCKED_GROUP + "/artifacts/public-agent")
                .then().statusCode(403);
    }

    @Test
    void anonymousCallerWithoutGrantsDoesNotDiscoverPublicAgents() {
        assertThat(wellKnownAgents(given()), empty());
        assertThat(ardSearch(given()), empty());
    }

    @Test
    void adminDiscoversEveryAgent() {
        List<String> all = List.of(OPEN_GROUP + "/public-agent", OPEN_GROUP + "/entitled-agent",
                LOCKED_GROUP + "/public-agent", LOCKED_GROUP + "/entitled-agent");
        assertThat(wellKnownAgents(as("alice")), containsInAnyOrder(all.toArray()));
        assertThat(ardSearch(as("alice")), containsInAnyOrder(all.toArray()));
    }
}
