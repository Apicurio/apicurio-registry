package io.apicurio.registry.headers;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

// Verifies the HSTS header on a 500 response (#2411). A 500 has no natural trigger, so HstsBoomResource
// provides a test-only endpoint that throws, enabled only for this test via the alternative below.
@QuarkusTest
@TestProfile(HstsError500Test.BoomProfile.class)
public class HstsError500Test {

    public static class BoomProfile implements QuarkusTestProfile {
        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(HstsBoomResource.class);
        }
    }

    private static final String HSTS = "Strict-Transport-Security";

    @Test
    public void testHstsOnInternalServerError500() {
        given().when().get("/apis/test/hsts-500-boom").then().statusCode(500).header(HSTS,
                containsString("max-age="));
    }
}
