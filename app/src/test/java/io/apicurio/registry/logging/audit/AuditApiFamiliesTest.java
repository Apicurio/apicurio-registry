package io.apicurio.registry.logging.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One real request through the filter for each API family not exercised by {@code AuditEventTest}:
 * ccompat v7, the Iceberg REST catalog, the MCP Registry API and the {@code /.well-known} endpoints. These
 * check the parts the coverage test only sees statically: the API name derived from the generated or
 * hand-written resource, the operation, and the resolution of the implementing bean.
 */
@QuarkusTest
@TestProfile(AuditApiFamiliesTest.Profile.class)
public class AuditApiFamiliesTest extends AbstractResourceTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "apicurio.features.experimental.enabled", "true",
                    "apicurio.iceberg.enabled", "true",
                    "apicurio.mcp-registry.enabled", "true",
                    "apicurio.ard.enabled", "true");
        }
    }

    private AuditLogCapture capture;

    @BeforeEach
    void captureAuditLog() {
        capture = new AuditLogCapture();
        capture.assertCapturing();
    }

    @AfterEach
    void releaseAuditLog() {
        if (capture != null) {
            capture.close();
        }
    }

    private JsonNode eventFor(Response response) throws Exception {
        String auditId = response.getHeader(AuditEventFilter.AUDIT_ID_HEADER);
        assertNotNull(auditId, "Audit-ID header missing");
        List<String> lines = capture.lines().stream().filter(l -> l.contains(auditId)).toList();
        assertEquals(1, lines.size(), "audit lines for " + auditId + ": " + capture.lines());
        return MAPPER.readTree(lines.get(0));
    }

    @Test
    void ccompatV7Mutation() throws Exception {
        String subject = "audit-" + UUID.randomUUID();
        Response response = given().contentType(ContentType.JSON)
                .body("{\"schema\": \"{\\\"type\\\": \\\"string\\\"}\"}")
                .post("/ccompat/v7/subjects/{subject}/versions", subject);
        assertEquals(200, response.statusCode());
        JsonNode event = eventFor(response);
        assertEquals("ccompat-v7", event.get("api").asText());
        assertEquals("registerSchemaUnderSubject", event.get("operation").asText());
        assertEquals(subject, event.at("/objectRef/name").asText());
        assertEquals("system:anonymous", event.at("/user/username").asText(), "authentication is off");
        assertEquals("success", event.get("outcome").asText());
    }

    @Test
    void icebergMutations() throws Exception {
        String namespace = "audit_" + UUID.randomUUID().toString().replace("-", "");
        Response created = given().contentType(ContentType.JSON)
                .body("{\"namespace\": [\"" + namespace + "\"]}")
                .post("/iceberg/v1/default/namespaces");
        assertEquals(200, created.statusCode());
        JsonNode event = eventFor(created);
        assertEquals("iceberg-v1", event.get("api").asText());
        assertEquals("createNamespace", event.get("operation").asText());
        assertEquals("namespaces", event.at("/objectRef/resource").asText());

        Response dropped = given().delete("/iceberg/v1/default/namespaces/{namespace}", namespace);
        assertEquals(204, dropped.statusCode());
        event = eventFor(dropped);
        assertEquals("dropNamespace", event.get("operation").asText());
        assertEquals(namespace, event.at("/objectRef/namespace").asText());
        assertTrue(event.at("/objectRef/name").isMissingNode(), "the catalog prefix is not an identity");
        assertEquals("success", event.get("outcome").asText());
    }

    @Test
    void mcpRegistryFailedMutation() throws Exception {
        Response response = given().delete("/mcp-registry/v0.1/servers/{serverName}/versions/{version}",
                "audit-missing-server", "1.0.0");
        assertTrue(response.statusCode() >= 400, "deleting a missing server fails: " + response.statusCode());
        JsonNode event = eventFor(response);
        assertEquals("mcp-registry-v0.1", event.get("api").asText());
        assertEquals("deleteServerVersion", event.get("operation").asText());
        assertEquals("audit-missing-server", event.at("/objectRef/name").asText());
        assertEquals("1.0.0", event.at("/objectRef/version").asText());
        assertEquals(response.statusCode(), event.at("/responseStatus/code").asInt());
        assertEquals("failure", event.get("outcome").asText());
    }

    @Test
    void wellKnownExemptSearchIsNotAudited() {
        Response response = given().contentType(ContentType.JSON).body("{}")
                .post(registryApiBaseUrl.replace("/apis", "") + "/.well-known/ard/search");
        assertNotEquals(404, response.statusCode(), "the ARD endpoint is enabled in this profile");
        assertNotEquals(401, response.statusCode());
        assertNotEquals(403, response.statusCode());
        assertNull(response.getHeader(AuditEventFilter.AUDIT_ID_HEADER));
        assertTrue(capture.lines().stream().noneMatch(l -> l.contains("\"ardSearch\"")),
                capture.lines().toString());
    }
}
