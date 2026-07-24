package io.apicurio.registry.headers;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalToIgnoringCase;

// #2412: ContentTypeOptionsFilter runs first so the nosniff header survives every response type,
// including the ones that short-circuit the chain (disabled-API 404 resets, redirects).
@QuarkusTest
@TestProfile(ContentTypeOptionsHeaderTest.DisabledApisProfile.class)
public class ContentTypeOptionsHeaderTest {

    private static final String CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";

    public static class DisabledApisProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("apicurio.disable.apis", "/apis/registry/v3/system/.*");
        }
    }

    @Test
    public void testNoSniffOnSuccessResponse() {
        given().when().get("/apis/registry/v3/groups").then().statusCode(200)
                .header(CONTENT_TYPE_OPTIONS, equalToIgnoringCase("nosniff"));
    }

    @Test
    public void testNoSniffOnApiNotFoundError() {
        given().when().get("/apis/registry/v3/groups/default/artifacts/does-not-exist-" + System.nanoTime())
                .then().statusCode(404).header(CONTENT_TYPE_OPTIONS, equalToIgnoringCase("nosniff"));
    }

    @Test
    public void testNoSniffOnUnknownPath404() {
        given().when().get("/this-path-does-not-exist").then().statusCode(404)
                .header(CONTENT_TYPE_OPTIONS, equalToIgnoringCase("nosniff"));
    }

    // The disabled-API path calls response.reset(), which clears all headers; this asserts the nosniff
    // header is re-applied there (the case that motivated the reset-keeping-headers helper).
    @Test
    public void testNoSniffOnDisabledApi404() {
        given().when().get("/apis/registry/v3/system/info").then().statusCode(404)
                .header(CONTENT_TYPE_OPTIONS, equalToIgnoringCase("nosniff"));
    }

    @Test
    public void testNoSniffOnBadRequest() {
        given().contentType("application/json").body("{ not valid json").when()
                .post("/apis/registry/v3/groups").then().statusCode(400)
                .header(CONTENT_TYPE_OPTIONS, equalToIgnoringCase("nosniff"));
    }
}
