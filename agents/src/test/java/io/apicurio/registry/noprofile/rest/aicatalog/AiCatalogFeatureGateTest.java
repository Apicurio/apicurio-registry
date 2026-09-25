package io.apicurio.registry.noprofile.rest.aicatalog;

import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the AI Catalog and ARD well-known endpoints when experimental/A2A/MCP are on
 * but AI Catalog and ARD are off.
 */
@QuarkusTest
@TestProfile(AiCatalogDisabledProfile.class)
public class AiCatalogFeatureGateTest extends AbstractResourceTestBase {

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
    public void testAiCatalogBlockedWhenAiCatalogDisabled() {
        givenAtRoot()
                .when()
                .get("/.well-known/ai-catalog.json")
                .then()
                .statusCode(404);
    }

    @Test
    public void testArdSearchBlockedWhenArdDisabled() {
        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .body("{\"query\": {\"text\": \"anything\"}}")
                .post("/.well-known/ard/search")
                .then()
                .statusCode(404);
    }

    @Test
    public void testArdListAgentsBlockedWhenArdDisabled() {
        givenAtRoot()
                .when()
                .get("/.well-known/ard/agents")
                .then()
                .statusCode(404);
    }

    @Test
    public void testArdExploreBlockedWhenArdDisabled() {
        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .body("{\"resultType\": {\"facets\": [{\"field\": \"type\"}]}}")
                .post("/.well-known/ard/explore")
                .then()
                .statusCode(404);
    }
}
