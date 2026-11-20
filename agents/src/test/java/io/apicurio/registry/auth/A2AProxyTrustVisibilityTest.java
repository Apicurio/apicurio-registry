package io.apicurio.registry.auth;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.ProxyHeaderTrustAuthTestProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.specification.RequestSpecification;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

/**
 * With {@code apicurio.authn.proxy-header.trust-proxy-authorization}, the proxy has already authorized the
 * request, so the registry grants it read access regardless of roles. "entitled" Agent Cards must then be
 * discoverable by proxy-authenticated users without a registry role, while private cards stay owner-only.
 */
@QuarkusTest
@TestProfile(A2AProxyTrustVisibilityTest.Profile.class)
@Tag(ApicurioTestTags.SLOW)
public class A2AProxyTrustVisibilityTest extends AbstractResourceTestBase {

    public static class Profile extends ProxyHeaderTrustAuthTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> props = new HashMap<>(super.getConfigOverrides());
            props.put("apicurio.features.experimental.enabled", "true");
            props.put("apicurio.a2a.enabled", "true");
            return props;
        }
    }

    private static final String AGENT_CARD = """
            {"name":"ProxyAgent","description":"d","version":"1.0.0",
             "supportedInterfaces":[{"url":"https://example.com/agent","protocolBinding":"http+json","protocolVersion":"1.0"}],
             "capabilities":{"streaming":false,"pushNotifications":false},
             "skills":[{"id":"s","name":"S","description":"d","tags":["t"]}],
             "defaultInputModes":["text"],"defaultOutputModes":["text"]}
            """;

    @Override
    protected void deleteGlobalRules(int expectedDefaultRulesCount) {
        // Global rules cannot be reset without proxy headers; this test does not use them.
    }

    @Test
    public void testTrustedProxyUserWithoutRoleSeesEntitledButNotOthersPrivate() {
        String groupId = TestUtils.generateGroupId();
        String entitledId = "trusted-proxy-entitled-agent";
        String privateId = "trusted-proxy-private-agent";
        create(groupId, entitledId, null);
        create(groupId, privateId, "private");

        // No registry role, but the proxy is trusted to have authorized the request.
        asUser("roleless-user", "").when().get("/.well-known/agents?limit=500")
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
        asUser("owner-user", "sr-developer").contentType("application/json").body(body)
                .when().post("/apis/registry/v3/groups/" + groupId + "/artifacts")
                .then().statusCode(200);
    }

    private RequestSpecification asUser(String user, String groups) {
        return atRoot().header("X-Forwarded-User", user)
                .header("X-Forwarded-Email", user + "@example.com")
                .header("X-Forwarded-Groups", groups);
    }

    private static RequestSpecification atRoot() {
        int port = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
        return given().baseUri("http://localhost:" + port);
    }
}
