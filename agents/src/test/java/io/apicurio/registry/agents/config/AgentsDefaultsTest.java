package io.apicurio.registry.agents.config;

import io.apicurio.registry.agents.a2a.A2AConfig;
import io.apicurio.registry.agents.aicatalog.AiCatalogConfig;
import io.apicurio.registry.agents.ard.ArdConfig;
import io.apicurio.registry.agents.mcpimport.McpImportConfig;
import io.apicurio.registry.agents.mcpregistry.McpRegistryConfig;
import io.apicurio.registry.agents.mcptools.McpToolsConfig;
import io.apicurio.registry.federation.FederationConfig;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * With nothing set, every default is what it was before the umbrella and shared defaults existed.
 */
@QuarkusTest
public class AgentsDefaultsTest {

    @Inject
    A2AConfig a2a;

    @Inject
    McpToolsConfig mcpTools;

    @Inject
    McpRegistryConfig mcpRegistry;

    @Inject
    AiCatalogConfig aiCatalog;

    @Inject
    ArdConfig ard;

    @Inject
    McpImportConfig mcpImport;

    @Inject
    FederationConfig federation;

    @Test
    void everythingStaysOffAndNamedAsBefore() {
        assertFalse(a2a.isEnabled());
        assertFalse(mcpTools.isEnabled());
        assertFalse(mcpRegistry.isEnabled());
        assertFalse(aiCatalog.isEnabled());
        assertFalse(ard.isEnabled());
        assertFalse(mcpImport.isInsecureHttpEnabled());
        assertFalse(mcpImport.isLoopbackEnabled());
        assertFalse(federation.isInsecureHttpEnabled());
        assertFalse(federation.isLoopbackEnabled());
        assertEquals("Apicurio Registry", a2a.getAgentName());
        assertEquals("Apicurio Registry", aiCatalog.getHostName());
        given().get("/apis/registry/v3/system/uiConfig").then().statusCode(200)
                .body("features.agents", equalTo(false));
    }
}
