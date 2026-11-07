package io.apicurio.registry.logging.audit;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * With {@code apicurio.audit.log.enabled=false} the event is still produced, so the response carries its
 * {@code Audit-ID}, but nothing is written to the audit logger.
 */
@QuarkusTest
@TestProfile(AuditLogDisabledTest.Profile.class)
public class AuditLogDisabledTest extends AbstractResourceTestBase {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("apicurio.audit.log.enabled", "false");
        }
    }

    @Test
    void eventIsNotLogged() {
        try (AuditLogCapture capture = new AuditLogCapture()) {
            capture.assertCapturing();
            Response response = given().contentType(ContentType.JSON)
                    .body("{\"groupId\": \"" + TestUtils.generateGroupId() + "\"}")
                    .post("/registry/v3/groups");
            assertEquals(200, response.statusCode());
            String auditId = response.getHeader(AuditEventFilter.AUDIT_ID_HEADER);
            assertNotNull(auditId, "Audit-ID header missing");
            assertEquals(auditId, UUID.fromString(auditId).toString());
            assertEquals(0, capture.lines().size(), capture.lines().toString());
        }
    }
}
