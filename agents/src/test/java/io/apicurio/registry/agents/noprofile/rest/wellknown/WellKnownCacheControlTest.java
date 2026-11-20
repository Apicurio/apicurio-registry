package io.apicurio.registry.agents.noprofile.rest.wellknown;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.agents.noprofile.rest.aicatalog.AiCatalogEnabledProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Discovery responses depend on the caller's access and change with the registry's content, so no
 * cache may keep them.
 */
@QuarkusTest
@TestProfile(AiCatalogEnabledProfile.class)
public class WellKnownCacheControlTest extends AbstractResourceTestBase {

    private static final String NO_CACHE = "no-cache, no-store, must-revalidate";

    private static String root() {
        return "http://localhost:" + ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
    }

    private static void assertNotCacheable(String what, Response response) {
        assertEquals(NO_CACHE, response.header("Cache-Control"), what);
    }

    @Test
    void discoveryResponsesAreNotCacheable() {
        for (String path : List.of("/.well-known/agents", "/.well-known/agent.json", "/.well-known/agent-card.json",
                "/.well-known/mcp-tools", "/.well-known/mcp-tools/default/missing", "/.well-known/ai-catalog.json",
                "/.well-known/ard.json", "/.well-known/ard/agents")) {
            assertNotCacheable(path, given().baseUri(root()).basePath("").get(path));
        }
        assertNotCacheable("/.well-known/ard/search", given().baseUri(root()).basePath("")
                .contentType(CT_JSON).body("{\"query\":{\"text\":\"x\"}}").post("/.well-known/ard/search"));
    }
}
