package io.apicurio.registry.noprofile.rest.a2a;

import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests that A2A endpoints are blocked when the experimental features gate is disabled (default).
 */
@QuarkusTest
public class ExperimentalFeatureGateTest extends AbstractResourceTestBase {

    private String serverRootUrl;

    @BeforeEach
    public void setUp() {
        int port = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
        serverRootUrl = "http://localhost:" + port;
    }

    private RequestSpecification givenAtRoot() {
        return RestAssured.given().baseUri(serverRootUrl);
    }

    @Test
    public void testAgentCardBlockedWhenExperimentalDisabled() {
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .get("/.well-known/agent.json")
                .then()
                .statusCode(404);
    }

    @Test
    public void testAgentSearchBlockedWhenExperimentalDisabled() {
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .get("/.well-known/agents")
                .then()
                .statusCode(404);
    }

    @Test
    public void testLlmSchemaBlockedWhenExperimentalDisabled() {
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .get("/.well-known/schemas/prompt-template/v1")
                .then()
                .statusCode(404);
    }

    @Test
    public void testModelSchemaBlockedWhenExperimentalDisabled() {
        givenAtRoot()
                .when()
                .contentType(CT_JSON)
                .get("/.well-known/schemas/model-schema/v1")
                .then()
                .statusCode(404);
    }
}
