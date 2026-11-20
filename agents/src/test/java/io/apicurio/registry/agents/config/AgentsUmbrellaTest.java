package io.apicurio.registry.agents.config;

import io.apicurio.common.apps.config.ExperimentalConfigPropertyDef;
import io.apicurio.common.apps.config.ExperimentalConfigPropertyList;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.agents.a2a.A2AConfig;
import io.apicurio.registry.agents.aicatalog.AiCatalogConfig;
import io.apicurio.registry.agents.ard.ArdConfig;
import io.apicurio.registry.agents.mcpimport.McpImportConfig;
import io.apicurio.registry.agents.mcptools.McpToolsConfig;
import io.apicurio.registry.federation.FederationConfig;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code apicurio.agents.enabled} turns on every discovery feature, a feature's own property still
 * wins, and the features that connect to other servers stay off.
 */
@QuarkusTest
@TestProfile(AgentsUmbrellaTestProfile.class)
public class AgentsUmbrellaTest extends AbstractResourceTestBase {

    @Inject
    A2AConfig a2a;

    @Inject
    AiCatalogConfig aiCatalog;

    @Inject
    McpImportConfig mcpImport;

    @Inject
    FederationConfig federation;

    @Inject
    ArdConfig ard;

    @Inject
    McpToolsConfig mcpTools;

    @Inject
    ExperimentalConfigPropertyList experimental;

    private RequestSpecification atRoot() {
        return given().baseUri("http://localhost:"
                + ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class)).basePath("");
    }

    @Test
    void discoveryFeaturesFollowTheUmbrella() {
        atRoot().get("/.well-known/agents").then().statusCode(200).body("agents", notNullValue());
        atRoot().get("/.well-known/ai-catalog.json").then().statusCode(200)
                .body("host.displayName", equalTo("Acme Agents"));
        atRoot().get("/apis/mcp-registry/v0.1/servers").then().statusCode(200).body("servers", notNullValue());
        assertTrue(ard.isEnabled());
        atRoot().get("/.well-known/ard.json").then().statusCode(200);
        given().get(registryV3ApiUrl + "/system/uiConfig").then().statusCode(200)
                .body("features.agents", equalTo(true));
    }

    @Test
    void anExplicitFeaturePropertyWins() {
        assertFalse(mcpTools.isEnabled());
        atRoot().get("/.well-known/mcp-tools").then().statusCode(404);
    }

    @Test
    void featuresThatConnectToOtherServersStayOff() {
        assertFalse(mcpImport.isEnabled());
        assertFalse(federation.isEnabled());
        given().get(registryV3ApiUrl + "/system/uiConfig").then().body("features.mcpImport", equalTo(false));
    }

    @Test
    void theUmbrellaIsBehindTheExperimentalGate() {
        assertTrue(experimental.getExperimentalConfigProperties().stream()
                .map(ExperimentalConfigPropertyDef::getName).anyMatch("apicurio.agents.enabled"::equals));
    }

    @Test
    void sharedDefaultsApplyUnlessAFeatureSetsItsOwn() {
        assertEquals("Acme Agents", a2a.getAgentName());
        assertEquals("Acme Agents", aiCatalog.getHostName());
        assertTrue(mcpImport.isLoopbackEnabled());
        assertFalse(federation.isLoopbackEnabled());
        assertTrue(mcpImport.isInsecureHttpEnabled());
        assertTrue(federation.isInsecureHttpEnabled());
        atRoot().get("/.well-known/agent-card.json").then().statusCode(200).body("name", equalTo("Acme Agents"))
                .body("supportedInterfaces[0].protocolVersion", equalTo(A2AConfig.PROTOCOL_VERSION));
    }
}
