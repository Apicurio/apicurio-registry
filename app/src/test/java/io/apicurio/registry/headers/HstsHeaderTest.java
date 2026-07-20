package io.apicurio.registry.headers;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

// #2411: the HSTS header is set by a servlet filter, so error responses that bypass the chain lose it.
@QuarkusTest
@TestProfile(HstsHeaderTest.DisabledApisProfile.class)
public class HstsHeaderTest {

    private static final String HSTS = "Strict-Transport-Security";

    public static class DisabledApisProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("apicurio.disable.apis", "/apis/registry/v3/system/.*");
        }
    }

    @Test
    public void testHstsOnSuccessResponse() {
        given().when().get("/apis/registry/v3/groups").then().statusCode(200).header(HSTS,
                containsString("max-age="));
    }

    // #8713: RFC 6797 defines the directive as "includeSubDomains" (capital D); pin the exact
    // value so a regression to the invalid "includeSubdomains" casing is caught.
    @Test
    public void testHstsDirectiveCasingIsRfcCompliant() {
        given().when().get("/apis/registry/v3/groups").then().statusCode(200).header(HSTS,
                equalTo("max-age=31536000; includeSubDomains"));
    }

    @Test
    public void testHstsOnApiNotFoundError() {
        given().when().get("/apis/registry/v3/groups/default/artifacts/does-not-exist-" + System.nanoTime())
                .then().statusCode(404).header(HSTS, containsString("max-age="));
    }

    // #8713: the filter chain (see #2411) also covers error responses, so pin the exact
    // directive casing there too, not just on success responses.
    @Test
    public void testHstsDirectiveCasingIsRfcCompliantOn404() {
        given().when().get("/apis/registry/v3/groups/default/artifacts/does-not-exist-" + System.nanoTime())
                .then().statusCode(404).header(HSTS, equalTo("max-age=31536000; includeSubDomains"));
    }

    @Test
    public void testHstsOnUnknownPath404() {
        given().when().get("/this-path-does-not-exist").then().statusCode(404).header(HSTS,
                containsString("max-age="));
    }

    @Test
    public void testHstsOnDisabledApi404() {
        given().when().get("/apis/registry/v3/system/info").then().statusCode(404).header(HSTS,
                containsString("max-age="));
    }

    @Test
    public void testHstsOnBadRequest() {
        given().contentType("application/json").body("{ not valid json").when()
                .post("/apis/registry/v3/groups").then().statusCode(400).header(HSTS,
                        containsString("max-age="));
    }
}
