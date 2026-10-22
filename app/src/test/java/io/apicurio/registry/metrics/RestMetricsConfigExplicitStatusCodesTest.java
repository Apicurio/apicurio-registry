package io.apicurio.registry.metrics;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;

@QuarkusTest
@TestProfile(RestMetricsConfigExplicitStatusCodesTest.RestMetricsDisabledTestProfile.class)
public class RestMetricsConfigExplicitStatusCodesTest extends AbstractRestMetricsTest {

    public static class RestMetricsDisabledTestProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "apicurio.metrics.rest.explicit-status-codes-list", "200,404" // These are the ones we can test easily.
            );
        }
    }

    @Test
    public void test() {

        // NOTE: This relies on a specific tag ordering. If this starts to fail, figure out something better.

        lineDoesMatch(".*method=\"\\(unspecified\\)\",path=\"\\(unspecified\\)\",status_code_group=\"1xx\".*0\\.0");
        lineDoesMatch(".*method=\"\\(unspecified\\)\",path=\"\\(unspecified\\)\",status_code_group=\"2xx\".*0\\.0");
        lineDoesMatch(".*method=\"\\(unspecified\\)\",path=\"\\(unspecified\\)\",status_code_group=\"200\".*0\\.0");
        lineDoesMatch(".*method=\"\\(unspecified\\)\",path=\"\\(unspecified\\)\",status_code_group=\"3xx\".*0\\.0");
        lineDoesNotMatch(".*method=\"\\(unspecified\\)\",path=\"\\(unspecified\\)\",status_code_group=\"401\".*0\\.0");
        lineDoesMatch(".*method=\"\\(unspecified\\)\",path=\"\\(unspecified\\)\",status_code_group=\"404\".*0\\.0");
        lineDoesMatch(".*method=\"\\(unspecified\\)\",path=\"\\(unspecified\\)\",status_code_group=\"4xx\".*0\\.0");
        lineDoesMatch(".*method=\"\\(unspecified\\)\",path=\"\\(unspecified\\)\",status_code_group=\"5xx\".*0\\.0");

        given()
                .when().get(baseURI + "/apis/registry/v3/groups")
                .then()
                .log().all()
                .statusCode(200);

        given()
                .when().get(baseURI + "/apis/registry/v3/blahblah")
                .then()
                .log().all()
                .statusCode(404);

        metrics(true);

        lineDoesMatch(".*method=\"GET\",path=\"/apis/registry/v3/groups\",status_code_group=\"200\".*1\\.0");
        lineDoesNotMatch(".*method=\"GET\",path=\"/apis/registry/v3/groups\",status_code_group=\"2xx\".*1\\.0");

        lineDoesMatch(".*method=\"GET\",path=\"\\(unspecified\\)\",status_code_group=\"404\".*1\\.0");
        lineDoesNotMatch(".*method=\"GET\",path=\"\\(unspecified\\)\",status_code_group=\"4xx\".*1\\.0");
    }
}
