package io.apicurio.registry.cors;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Reproduces GitHub issue #4446: behind an HTTPS reverse proxy, non-GET requests fail with 403 because
 * the browser's origin does not match the hardcoded CORS origins. The CorsConfigInterceptor auto-detects
 * origins from apicurio.url.override.host/port, which users already configure for proxy setups.
 */
@QuarkusTest
@TestProfile(CorsUrlOverrideTest.ReverseProxyTestProfile.class)
public class CorsUrlOverrideTest {

    private static final String PROXY_HOST = "apicurio.example.com";
    private static final String PROXY_ORIGIN = "https://" + PROXY_HOST;

    /**
     * Mirrors the reporter's environment: HTTPS reverse proxy on port 443 with URL overrides configured.
     */
    public static class ReverseProxyTestProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "apicurio.url.override.host", PROXY_HOST,
                    "apicurio.url.override.port", "443"
            );
        }
    }

    /**
     * The core scenario from issue #4446: a DELETE preflight from the proxy origin must succeed.
     * Without the fix, this returns 403 because the origin is not in the allowed list.
     */
    @Test
    public void testDeletePreflightFromProxyOrigin() {
        given()
                .header("Origin", PROXY_ORIGIN)
                .header("Access-Control-Request-Method", "DELETE")
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .when()
                .options("/apis/registry/v3/groups/default/artifacts")
                .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", PROXY_ORIGIN)
                .header("Access-Control-Allow-Methods", notNullValue());
    }

    /**
     * After preflight passes, the actual POST request (e.g. creating a group) must also include
     * CORS response headers so the browser accepts the response.
     */
    @Test
    public void testPostRequestFromProxyOriginIncludesCorsHeaders() {
        String groupId = "cors-test-" + UUID.randomUUID();
        given()
                .header("Origin", PROXY_ORIGIN)
                .header("Content-Type", "application/json")
                .body("{\"groupId\": \"" + groupId + "\"}")
                .when()
                .post("/apis/registry/v3/groups")
                .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", PROXY_ORIGIN);
    }

    /**
     * DELETE request from the proxy origin must include CORS headers (the exact method that
     * failed in #4446). We create a group first, then delete it with the proxy origin.
     */
    @Test
    public void testDeleteRequestFromProxyOriginIncludesCorsHeaders() {
        String groupId = "cors-delete-test-" + UUID.randomUUID();

        // Create a group first
        given()
                .header("Content-Type", "application/json")
                .body("{\"groupId\": \"" + groupId + "\"}")
                .when()
                .post("/apis/registry/v3/groups")
                .then()
                .statusCode(200);

        // DELETE preflight from the proxy origin — this is the exact request that failed in #4446.
        // The browser sends OPTIONS before DELETE; if this returns 403, the DELETE never happens.
        given()
                .header("Origin", PROXY_ORIGIN)
                .header("Access-Control-Request-Method", "DELETE")
                .header("Access-Control-Request-Headers", "authorization")
                .when()
                .options("/apis/registry/v3/groups/" + groupId)
                .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", PROXY_ORIGIN)
                .header("Access-Control-Allow-Methods", notNullValue());

        // The actual DELETE after preflight succeeds
        given()
                .header("Origin", PROXY_ORIGIN)
                .when()
                .delete("/apis/registry/v3/groups/" + groupId)
                .then()
                .header("Access-Control-Allow-Origin", PROXY_ORIGIN);
    }

    /**
     * An origin not derived from the URL overrides must still be rejected.
     */
    @Test
    public void testUnknownOriginIsRejected() {
        given()
                .header("Origin", "https://unknown.example.com")
                .header("Access-Control-Request-Method", "DELETE")
                .when()
                .options("/apis/registry/v3/groups")
                .then()
                .statusCode(403);
    }

    /**
     * The default localhost origins from application.properties must still work alongside
     * the auto-detected proxy origins.
     */
    @Test
    public void testDefaultLocalhostOriginStillWorks() {
        given()
                .header("Origin", "http://localhost:8888")
                .header("Access-Control-Request-Method", "POST")
                .when()
                .options("/apis/registry/v3/groups")
                .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", "http://localhost:8888");
    }
}
