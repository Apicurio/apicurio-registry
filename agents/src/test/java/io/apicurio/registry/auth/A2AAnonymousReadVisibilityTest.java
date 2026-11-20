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
 * With {@code apicurio.auth.anonymous-read-access.enabled}, anonymous callers may read artifacts through the
 * REST API. "entitled" Agent Cards must then be discoverable anonymously too, while private cards stay
 * hidden: an anonymous caller has no identity to match the owner.
 */
@QuarkusTest
@TestProfile(A2AAnonymousReadVisibilityTest.Profile.class)
@Tag(ApicurioTestTags.SLOW)
public class A2AAnonymousReadVisibilityTest extends AbstractResourceTestBase {

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
            map.put("apicurio.auth.anonymous-read-access.enabled", "true");
            map.put("quarkus.security.users.embedded.enabled", "true");
            map.put("quarkus.security.users.embedded.plain-text", "true");
            map.put("quarkus.security.users.embedded.users.alice", "alice");
            map.put("quarkus.security.users.embedded.roles.alice", "sr-admin");
            return map;
        }
    }

    private static final String AGENT_CARD = """
            {"name":"AnonAgent","description":"d","version":"1.0.0",
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
    public void testAnonymousSeesEntitledButNotPrivateWhenAnonymousReadAccessEnabled() {
        String groupId = TestUtils.generateGroupId();
        String entitledId = "anon-read-entitled-agent";
        String privateId = "anon-read-private-agent";
        create(groupId, entitledId, null);
        create(groupId, privateId, "private");

        // Anonymous callers may read the entitled card through the REST API...
        atRoot().when().get("/apis/registry/v3/groups/" + groupId + "/artifacts/" + entitledId)
                .then().statusCode(200);

        // ...so discovery shows it to them; the private card stays hidden.
        atRoot().when().get("/.well-known/agents?limit=500")
                .then().statusCode(200)
                .body("agents.artifactId", hasItem(entitledId))
                .body("agents.artifactId", not(hasItem(privateId)));
    }

    private void create(String groupId, String artifactId, String visibility) {
        Map<String, Object> content = new HashMap<>();
        content.put("content", AGENT_CARD);
        content.put("contentType", "application/json");
        Map<String, Object> firstVersion = new HashMap<>();
        firstVersion.put("content", content);
        Map<String, Object> body = new HashMap<>();
        body.put("artifactId", artifactId);
        body.put("artifactType", "AGENT_CARD");
        if (visibility != null) {
            body.put("labels", Map.of("apicurio.agent.visibility", visibility));
        }
        body.put("firstVersion", firstVersion);
        atRoot().auth().preemptive().basic("alice", "alice").contentType("application/json").body(body)
                .when().post("/apis/registry/v3/groups/" + groupId + "/artifacts")
                .then().statusCode(200);
    }

    private static RequestSpecification atRoot() {
        int port = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
        return given().baseUri("http://localhost:" + port);
    }
}
