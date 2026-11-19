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

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * With {@code apicurio.audit.enabled=false} a mutation produces neither an audit line nor an
 * {@code Audit-ID} header.
 */
@QuarkusTest
@TestProfile(AuditDisabledTest.Profile.class)
public class AuditDisabledTest extends AbstractResourceTestBase {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("apicurio.audit.enabled", "false");
        }
    }

    @Test
    void mutationIsNotAudited() {
        try (AuditLogCapture capture = new AuditLogCapture()) {
            capture.assertCapturing();
            Response response = given().contentType(ContentType.JSON)
                    .body("{\"groupId\": \"" + TestUtils.generateGroupId() + "\"}")
                    .post("/registry/v3/groups");
            assertEquals(200, response.statusCode());
            assertNull(response.getHeader(AuditEventFilter.AUDIT_ID_HEADER));
            assertEquals(0, capture.lines().size(), capture.lines().toString());
        }
    }
}
