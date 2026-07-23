package io.apicurio.registry.headers;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

// RedirectFilter returns early after sendRedirect(), bypassing HSTSFilter - the 302 must still get HSTS.
@QuarkusTest
@TestProfile(HstsRedirectHeaderTest.RedirectProfile.class)
public class HstsRedirectHeaderTest {

    private static final String HSTS = "Strict-Transport-Security";

    public static class RedirectProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("apicurio.redirects.enabled", "true", "apicurio.redirects.hsts-test",
                    "/hsts-redirect-source,/apis/registry/v3");
        }
    }

    @Test
    public void testHstsOnRedirect() {
        given().redirects().follow(false).when().get("/hsts-redirect-source").then().statusCode(302)
                .header(HSTS, containsString("max-age="));
    }
}
