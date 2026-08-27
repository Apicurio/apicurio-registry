package io.apicurio.registry.noprofile.ccompat.rest.v7;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.ccompat.rest.ContentTypes;
import io.apicurio.registry.ccompat.rest.v7.beans.RegisterSchemaRequest;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.Matchers.hasSize;

/**
 * Regression tests for subjects whose version count exceeds one page of results from the
 * storage layer. Listing must return every version (pagination over the complete set), and
 * deleting a subject must disable all of its versions so that the subject can be permanently
 * deleted afterwards.
 *
 * <p>A small page size is configured via {@link SmallPageSizeProfile} so that registering a
 * handful of versions is enough to span two pages and exercise the multi-page collection loop.
 */
@QuarkusTest
@TestProfile(CCompatV7LargeSubjectTest.SmallPageSizeProfile.class)
public class CCompatV7LargeSubjectTest extends AbstractResourceTestBase {

    /**
     * Test profile that sets a small page size so that multi-page collection is exercised
     * with only a handful of versions instead of thousands.
     */
    public static class SmallPageSizeProfile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("apicurio.ccompat.subject-versions-page-size", "5");
        }
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    private void registerVersion(String subject, int i) throws Exception {
        var schema = "{\"type\": \"record\", \"name\": \"R\", \"fields\": [{\"name\": \"f" + i
                + "\", \"type\": \"string\"}]}";
        var request = new RegisterSchemaRequest();
        request.setSchema(schema);
        request.setSchemaType("AVRO");

        given().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(request))
                .post("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200);
    }

    @Test
    public void testListAndDeleteSubjectWithMoreVersionsThanPageSize() throws Exception {
        var subject = TestUtils.generateSubject();

        // One more than the configured page size (5), so collecting the versions genuinely
        // spans two pages and the second page must be read.
        final int pageSize = 5;
        final int count = pageSize + 1;
        for (int i = 1; i <= count; i++) {
            registerVersion(subject, i);
        }

        // Full listing contains every version number, including those on the second page.
        given().when().get("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200)
                .body("$", hasSize(count))
                .body("$", hasItems(1, pageSize, count));

        // Offsets reaching into the second collected page return the correct remaining versions.
        given().when().queryParam("offset", pageSize).queryParam("limit", 2)
                .get("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200)
                .body("$", hasSize(1))
                .body("$", hasItems(count));

        // Same behaviour through the v8 API, which delegates to v7.
        given().when().get("/ccompat/v8/subjects/{subject}/versions", subject).then().statusCode(200)
                .body("$", hasSize(count));

        // Soft delete disables every version, so the default listing no longer finds the subject.
        given().when().delete("/ccompat/v7/subjects/{subject}", subject).then().statusCode(200)
                .body("$", hasSize(count));

        given().when().get("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(404);

        given().when().queryParam("deleted", true)
                .get("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200)
                .body("$", hasSize(count));

        // Permanent delete succeeds and reports every removed version.
        given().when().queryParam("permanent", true)
                .delete("/ccompat/v7/subjects/{subject}", subject).then().statusCode(200)
                .body("$", hasSize(count));
    }
}
