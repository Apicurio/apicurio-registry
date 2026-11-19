package io.apicurio.registry.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.logging.audit.AuditEventFilter;
import io.apicurio.registry.logging.audit.AuditLogCapture;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.ProxyHeaderAuthTestProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end checks of the unified audit event: one JSON line on the audit logger per audited request,
 * read back from the logger rather than from the in-memory event, with proxy-header authentication so the
 * caller and its roles are controlled by the test.
 */
@QuarkusTest
@TestProfile(ProxyHeaderAuthTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class AuditEventTest extends AbstractResourceTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEVELOPER = "audit-dev";
    private static final String READER = "audit-reader";
    private static final String ADMIN = "audit-admin";

    private AuditLogCapture capture;

    @Override
    protected void deleteGlobalRules(int expectedDefaultRulesCount) {
        // Every request needs proxy headers in this profile; the base class sends none.
    }

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

    private static RequestSpecification as(String user, String role) {
        return given().header("X-Forwarded-User", user).header("X-Forwarded-Groups", role);
    }

    private static String createArtifactBody(String artifactId, String content, String description) {
        return """
                {"artifactId": "%s", "artifactType": "JSON", "description": "%s",
                 "firstVersion": {"content": {"content": %s, "contentType": "application/json"}}}
                """.formatted(artifactId, description, MAPPER.valueToTree(content).toString());
    }

    /**
     * The single audit line produced by a request, found by the Audit-ID header the response carries.
     */
    private JsonNode eventFor(Response response) throws Exception {
        String auditId = response.getHeader(AuditEventFilter.AUDIT_ID_HEADER);
        assertNotNull(auditId, "Audit-ID header missing");
        assertEquals(auditId, UUID.fromString(auditId).toString(), "Audit-ID is a UUID");
        List<String> lines = capture.lines().stream().filter(l -> l.contains(auditId)).toList();
        assertEquals(1, lines.size(), "audit lines for " + auditId + ": " + capture.lines());
        JsonNode event = MAPPER.readTree(lines.get(0));
        assertEquals(auditId, event.get("auditId").asText());
        return event;
    }

    @Test
    void allowedMutationRecordsWhoWhatWhereAndWhen() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        Response response = as(DEVELOPER, "sr-developer")
                .header("X-Forwarded-For", "203.0.113.7, 198.51.100.2")
                .header("User-Agent", "audit-test/1.0")
                .contentType(ContentType.JSON)
                .body(createArtifactBody(artifactId, "{}", "plain"))
                .post("/registry/v3/groups/{groupId}/artifacts", groupId);
        assertEquals(200, response.statusCode());

        JsonNode event = eventFor(response);
        assertEquals("audit.registry.apicur.io/v1", event.get("apiVersion").asText());
        assertEquals("Event", event.get("kind").asText());
        assertEquals("Metadata", event.get("level").asText());
        assertEquals("ResponseComplete", event.get("stage").asText());
        assertEquals("post", event.get("verb").asText());
        assertEquals("/apis/registry/v3/groups/" + groupId + "/artifacts", event.get("requestURI").asText());
        assertEquals("v3", event.get("api").asText());
        assertEquals("createArtifact", event.get("operation").asText());
        assertEquals(DEVELOPER, event.at("/user/username").asText());
        assertEquals("[\"sr-developer\"]", event.at("/user/groups").toString());
        assertEquals("203.0.113.7, 198.51.100.2", event.get("forwardedFor").asText());
        assertEquals(3, event.get("sourceIPs").size());
        assertEquals("203.0.113.7", event.at("/sourceIPs/0").asText());
        assertEquals("198.51.100.2", event.at("/sourceIPs/1").asText());
        assertEquals("127.0.0.1", event.at("/sourceIPs/2").asText(), "the connection's peer comes last");
        assertEquals("audit-test/1.0", event.get("userAgent").asText());
        assertEquals("artifacts", event.at("/objectRef/resource").asText());
        assertEquals(groupId, event.at("/objectRef/groupId").asText());
        assertEquals(artifactId, event.at("/objectRef/artifactId").asText(), "taken from the response");
        assertEquals("1", event.at("/objectRef/version").asText());
        assertEquals(200, event.at("/responseStatus/code").asInt());
        assertTrue(event.at("/responseStatus/reason").isMissingNode());
        assertEquals("success", event.get("outcome").asText());
        String received = event.get("requestReceivedTimestamp").asText();
        String completed = event.get("stageTimestamp").asText();
        String timestamp = "\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d{6}Z";
        assertTrue(received.matches(timestamp), received);
        assertTrue(completed.matches(timestamp), completed);
        assertTrue(received.compareTo(completed) <= 0, received + " <= " + completed);
    }

    @Test
    void deniedMutationIsAuditedAsDenied() throws Exception {
        String groupId = TestUtils.generateGroupId();

        Response response = as(READER, "sr-readonly")
                .contentType(ContentType.JSON)
                .body(createArtifactBody(TestUtils.generateArtifactId(), "{}", "plain"))
                .post("/registry/v3/groups/{groupId}/artifacts", groupId);
        assertEquals(403, response.statusCode());

        JsonNode event = eventFor(response);
        assertEquals(READER, event.at("/user/username").asText());
        assertEquals("[\"sr-readonly\"]", event.at("/user/groups").toString());
        assertEquals("createArtifact", event.get("operation").asText());
        assertEquals(groupId, event.at("/objectRef/groupId").asText());
        assertTrue(event.at("/objectRef/artifactId").isMissingNode(), "a failed create names no artifact");
        assertEquals(403, event.at("/responseStatus/code").asInt());
        assertEquals("Forbidden", event.at("/responseStatus/reason").asText());
        assertEquals("denied", event.get("outcome").asText());
    }

    @Test
    void deniedReadIsAuditedButAllowedReadIsNot() throws Exception {
        String groupId = TestUtils.generateGroupId();

        Response anonymous = given().get("/registry/v3/groups/{groupId}/artifacts", groupId);
        assertEquals(401, anonymous.statusCode());
        JsonNode event = eventFor(anonymous);
        assertEquals("get", event.get("verb").asText());
        assertEquals("listArtifactsInGroup", event.get("operation").asText());
        assertEquals("system:anonymous", event.at("/user/username").asText());
        assertEquals("[]", event.at("/user/groups").toString());
        assertEquals("Unauthorized", event.at("/responseStatus/reason").asText());
        assertEquals("denied", event.get("outcome").asText());

        Response allowed = as(READER, "sr-readonly").get("/registry/v3/groups/{groupId}/artifacts", groupId);
        assertEquals(200, allowed.statusCode());
        assertNull(allowed.getHeader(AuditEventFilter.AUDIT_ID_HEADER));
        assertTrue(capture.lines().stream().noneMatch(l -> l.contains(groupId) && l.contains(READER)),
                capture.lines().toString());
    }

    @Test
    void exemptReadOnlyPostIsNotAudited() {
        Response response = as(READER, "sr-readonly")
                .contentType(ContentType.JSON)
                .body("{}")
                .post("/registry/v3/search/artifacts");
        assertEquals(200, response.statusCode());
        assertNull(response.getHeader(AuditEventFilter.AUDIT_ID_HEADER));
        assertTrue(capture.lines().stream().noneMatch(l -> l.contains("searchArtifactsByContent")),
                capture.lines().toString());
    }

    @Test
    void optedInReadIsAudited() throws Exception {
        Response response = as(ADMIN, "sr-admin").get("/registry/v3/admin/export");
        assertEquals(200, response.statusCode());
        JsonNode event = eventFor(response);
        assertEquals("get", event.get("verb").asText());
        assertEquals("exportData", event.get("operation").asText());
        assertEquals("success", event.get("outcome").asText());
    }

    @Test
    void previouslyUnauditedMutationsAndDelegatingApisEmitOneEvent() throws Exception {
        String groupId = TestUtils.generateGroupId();
        Response v2Group = as(DEVELOPER, "sr-developer")
                .contentType(ContentType.JSON)
                .body("{\"id\": \"" + groupId + "\"}")
                .post("/registry/v2/groups");
        assertEquals(200, v2Group.statusCode());
        JsonNode event = eventFor(v2Group);
        assertEquals("v2", event.get("api").asText());
        assertEquals("createGroup", event.get("operation").asText());
        assertEquals(groupId, event.at("/objectRef/groupId").asText(), "taken from the response");

        String subject = "audit-" + UUID.randomUUID();
        Response v8 = as(DEVELOPER, "sr-developer")
                .contentType(ContentType.JSON)
                .body("{\"schema\": \"{\\\"type\\\": \\\"string\\\"}\"}")
                .post("/ccompat/v8/subjects/{subject}/versions", subject);
        assertEquals(200, v8.statusCode());
        event = eventFor(v8);
        assertEquals("ccompat-v8", event.get("api").asText());
        assertEquals("registerSchemaUnderSubject", event.get("operation").asText());
        assertEquals("versions", event.at("/objectRef/resource").asText());
        assertEquals(subject, event.at("/objectRef/name").asText());
    }

    /**
     * AC #4: a failed and a denied mutation whose content, description, labels and headers carry a
     * unique marker produce events that contain neither the marker nor anything resembling an exception,
     * although the client's error response does quote the marker.
     */
    @Test
    void failedAndDeniedMutationsLeakNoContentSecretOrStackTrace() throws Exception {
        String marker = "AUDIT-CANARY-" + UUID.randomUUID();
        String groupId = TestUtils.generateGroupId();
        String body = createArtifactBody(marker, "{\"secret\": \"" + marker + "\"}", marker);

        Response created = as(DEVELOPER, "sr-developer").contentType(ContentType.JSON).body(body)
                .post("/registry/v3/groups/{groupId}/artifacts", groupId);
        assertEquals(200, created.statusCode());

        Response conflict = as(DEVELOPER, "sr-developer")
                .header("Authorization", "Bearer " + marker)
                .header("Cookie", "session=" + marker)
                .queryParam("token", marker)
                .contentType(ContentType.JSON).body(body)
                .post("/registry/v3/groups/{groupId}/artifacts", groupId);
        assertEquals(409, conflict.statusCode());
        assertTrue(conflict.asString().contains(marker), "the client error quotes the marker");
        JsonNode failed = eventFor(conflict);
        assertEquals("failure", failed.get("outcome").asText());
        assertEquals("Conflict", failed.at("/responseStatus/reason").asText());

        Response denied = as(READER, "sr-readonly")
                .header("Authorization", "Bearer " + marker)
                .contentType(ContentType.JSON)
                .body("{\"name\": \"" + marker + "\", \"description\": \"" + marker + "\", \"labels\": {\"k\": \""
                        + marker + "\"}}")
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}", groupId, "audit-denied");
        assertEquals(403, denied.statusCode());
        JsonNode deniedEvent = eventFor(denied);
        assertEquals("denied", deniedEvent.get("outcome").asText());

        for (JsonNode event : List.of(failed, deniedEvent)) {
            String json = event.toString();
            assertFalse(json.contains(marker), json);
            for (String forbidden : List.of("Exception", "Caused by", "\tat ", "\\tat ", "io.apicurio.",
                    "java.", "Bearer", "session=", "token")) {
                assertFalse(json.contains(forbidden), forbidden + " in " + json);
            }
        }
    }
}
