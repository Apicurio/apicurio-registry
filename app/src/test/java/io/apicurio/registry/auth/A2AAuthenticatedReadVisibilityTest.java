package io.apicurio.registry.auth;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.Vertx;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

/**
 * With {@code apicurio.auth.authenticated-read-access.enabled}, any authenticated user may read artifacts
 * even without a registry role. "entitled" Agent Cards must then be discoverable by such users too, since
 * "entitled" means entitled to read the card.
 */
@QuarkusTest
@TestProfile(A2AAuthenticatedReadVisibilityTest.Profile.class)
@Tag(ApicurioTestTags.SLOW)
public class A2AAuthenticatedReadVisibilityTest extends AbstractResourceTestBase {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> map = new HashMap<>();
            map.put("apicurio.features.experimental.enabled", "true");
            map.put("apicurio.a2a.enabled", "true");
            map.put("quarkus.oidc.tenant-enabled", "false");
            map.put("quarkus.http.auth.basic", "true");
            map.put("apicurio.auth.admin-override.enabled", "true");
            map.put("apicurio.auth.role-based-authorization", "true");
            map.put("apicurio.auth.authenticated-read-access.enabled", "true");
            map.put("quarkus.security.users.embedded.enabled", "true");
            map.put("quarkus.security.users.embedded.plain-text", "true");
            map.put("quarkus.security.users.embedded.users.alice", "alice");
            map.put("quarkus.security.users.embedded.roles.alice", "sr-admin");
            // No registry role.
            map.put("quarkus.security.users.embedded.users.eve", "eve");
            return map;
        }
    }

    private static final String AGENT_CARD = """
            {"name":"ReadAgent","description":"d","version":"1.0.0",
             "supportedInterfaces":[{"url":"https://example.com/agent","protocolBinding":"http+json","protocolVersion":"1.0"}],
             "capabilities":{"streaming":false,"pushNotifications":false},
             "skills":[{"id":"s","name":"S","description":"d","tags":["t"]}],
             "defaultInputModes":["text"],"defaultOutputModes":["text"]}
            """;

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(registryV3ApiUrl).vertx(vertx).basicAuth("alice", "alice"));
    }

    @Test
    public void testEntitledAgentVisibleWhenAuthenticatedReadAccessEnabled() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "authenticated-read-entitled-agent";

        Map<String, Object> content = new HashMap<>();
        content.put("content", AGENT_CARD);
        content.put("contentType", "application/json");
        Map<String, Object> firstVersion = new HashMap<>();
        firstVersion.put("content", content);
        Map<String, Object> body = new HashMap<>();
        body.put("artifactId", artifactId);
        body.put("artifactType", "AGENT_CARD");
        body.put("firstVersion", firstVersion);
        atRoot().auth().preemptive().basic("alice", "alice").contentType("application/json").body(body)
                .when().post("/apis/registry/v3/groups/" + groupId + "/artifacts")
                .then().statusCode(200);

        // eve has no role but may read the card through the REST API...
        atRoot().auth().preemptive().basic("eve", "eve")
                .when().get("/apis/registry/v3/groups/" + groupId + "/artifacts/" + artifactId)
                .then().statusCode(200);

        // ...so discovery shows it to her as well.
        atRoot().auth().preemptive().basic("eve", "eve")
                .when().get("/.well-known/agents?limit=500")
                .then().statusCode(200).body("agents.artifactId", hasItem(artifactId));

        // Still not to anonymous callers: "entitled" requires an authenticated caller.
        atRoot().when().get("/.well-known/agents?limit=500")
                .then().statusCode(200).body("agents.artifactId", not(hasItem(artifactId)));
    }

    private static RequestSpecification atRoot() {
        int port = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
        return given().baseUri("http://localhost:" + port);
    }
}
