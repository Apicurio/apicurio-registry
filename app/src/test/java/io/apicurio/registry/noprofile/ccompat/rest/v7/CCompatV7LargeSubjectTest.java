package io.apicurio.registry.noprofile.ccompat.rest.v7;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.ccompat.rest.ContentTypes;
import io.apicurio.registry.ccompat.rest.v7.beans.RegisterSchemaRequest;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.Matchers.hasSize;

/**
 * Regression tests for subjects whose version count exceeds one page of results from the
 * storage layer. Listing must return every version (pagination over the complete set), and
 * deleting a subject must disable all of its versions so that the subject can be permanently
 * deleted afterwards.
 *
 * <p>The page size is configured to an invalid 0 by {@link ClampedPageSizeProfile}, which
 * covers two things at once: the configured value is clamped to a minimum of 1 (0 would leave
 * the collection loop unable to advance), and a page size of 1 means every registered version
 * lands on its own page, so a handful of versions is enough to exercise multi-page collection.
 */
@QuarkusTest
@TestProfile(CCompatV7LargeSubjectTest.ClampedPageSizeProfile.class)
public class CCompatV7LargeSubjectTest extends AbstractResourceTestBase {

    /**
     * Test profile configuring an out-of-range page size, which the resource clamps to 1.
     */
    public static class ClampedPageSizeProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("apicurio.ccompat.subject-versions-page-size", "0");
        }
    }

    private static final int VERSION_COUNT = 6;

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

    // A page size that never advances would hang instead of failing, so bound the test.
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    @Test
    public void testListAndDeleteSubjectWithMoreVersionsThanPageSize() throws Exception {
        var subject = TestUtils.generateSubject();

        for (int i = 1; i <= VERSION_COUNT; i++) {
            registerVersion(subject, i);
        }

        // Full listing contains every version number, including those past the first page.
        given().when().get("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200)
                .body("$", hasSize(VERSION_COUNT))
                .body("$", hasItems(1, VERSION_COUNT));

        // Client-side offset/limit is applied to the complete set, not to the first page.
        given().when().queryParam("offset", VERSION_COUNT - 1).queryParam("limit", 2)
                .get("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200)
                .body("$", hasSize(1))
                .body("$", hasItems(VERSION_COUNT));

        // Same behaviour through the v8 API, which delegates to v7.
        given().when().get("/ccompat/v8/subjects/{subject}/versions", subject).then().statusCode(200)
                .body("$", hasSize(VERSION_COUNT));

        // Soft delete disables every version, so the default listing no longer finds the subject.
        given().when().delete("/ccompat/v7/subjects/{subject}", subject).then().statusCode(200)
                .body("$", hasSize(VERSION_COUNT));

        given().when().get("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(404);

        given().when().queryParam("deleted", true)
                .get("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200)
                .body("$", hasSize(VERSION_COUNT));

        // Permanent delete succeeds and reports every removed version.
        given().when().queryParam("permanent", true)
                .delete("/ccompat/v7/subjects/{subject}", subject).then().statusCode(200)
                .body("$", hasSize(VERSION_COUNT));
    }
}
