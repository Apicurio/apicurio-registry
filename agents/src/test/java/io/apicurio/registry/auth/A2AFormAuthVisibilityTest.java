package io.apicurio.registry.auth;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.FormAuthTestProfile;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Agent Card visibility under form authentication. Visibility filtering used to be applied only when OIDC
 * or basic auth was enabled, so with form login every card was returned to every caller.
 */
@QuarkusTest
@TestProfile(A2AFormAuthVisibilityTest.Profile.class)
@Tag(ApicurioTestTags.SLOW)
public class A2AFormAuthVisibilityTest extends AbstractResourceTestBase {

    public static class Profile extends FormAuthTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> props = new HashMap<>(super.getConfigOverrides());
            props.put("apicurio.features.experimental.enabled", "true");
            props.put("apicurio.a2a.enabled", "true");
            // A second developer, to check private cards stay owner-only.
            props.put("quarkus.security.users.embedded.users.developer2", "developer2");
            props.put("quarkus.security.users.embedded.roles.developer2", "sr-developer");
            return props;
        }
    }

    private static final String AGENT_CARD = """
            {"name":"FormAgent","description":"d","version":"1.0.0",
             "supportedInterfaces":[{"url":"https://example.com/agent","protocolBinding":"http+json","protocolVersion":"1.0"}],
             "capabilities":{"streaming":false,"pushNotifications":false},
             "skills":[{"id":"s","name":"S","description":"d","tags":["t"]}],
             "defaultInputModes":["text"],"defaultOutputModes":["text"]}
            """;

    @Override
    protected void deleteGlobalRules(int expectedDefaultRulesCount) {
        // Needs a login cookie; this test does not use global rules.
    }

    @Test
    public void testPrivateAgentHiddenFromOtherUsersAndAnonymous() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "form-private-agent";
        String ownerCookie = login("developer", "developer");

        Map<String, Object> content = new HashMap<>();
        content.put("content", AGENT_CARD);
        content.put("contentType", "application/json");
        Map<String, Object> firstVersion = new HashMap<>();
        firstVersion.put("content", content);
        Map<String, Object> body = new HashMap<>();
        body.put("artifactId", artifactId);
        body.put("artifactType", "AGENT_CARD");
        body.put("labels", Map.of("apicurio.agent.visibility", "private"));
        body.put("firstVersion", firstVersion);
        atRoot().cookie("quarkus-credential", ownerCookie).contentType("application/json").body(body)
                .when().post("/apis/registry/v3/groups/" + groupId + "/artifacts")
                .then().statusCode(200);

        atRoot().cookie("quarkus-credential", ownerCookie).when().get("/.well-known/agents?limit=500")
                .then().statusCode(200).body("agents.artifactId", hasItem(artifactId));

        atRoot().cookie("quarkus-credential", login("developer2", "developer2"))
                .when().get("/.well-known/agents?limit=500")
                .then().statusCode(200).body("agents.artifactId", not(hasItem(artifactId)));

        atRoot().when().get("/.well-known/agents?limit=500")
                .then().statusCode(200).body("agents.artifactId", not(hasItem(artifactId)));
    }

    private String login(String username, String password) {
        String cookie = atRoot().redirects().follow(false)
                .formParam("j_username", username).formParam("j_password", password)
                .when().post("/j_security_check")
                .then().statusCode(302).extract().cookie("quarkus-credential");
        assertNotNull(cookie, "form login must set the credential cookie");
        return cookie;
    }

    private static RequestSpecification atRoot() {
        int port = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
        return given().baseUri("http://localhost:" + port);
    }
}
