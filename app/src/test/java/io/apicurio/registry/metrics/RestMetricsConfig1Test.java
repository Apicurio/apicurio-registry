package io.apicurio.registry.metrics;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;

@QuarkusTest
@TestProfile(RestMetricsConfig1Test.RestMetricsDisabledTestProfile.class)
public class RestMetricsConfig1Test extends AbstractRestMetricsTest {

    public static class RestMetricsDisabledTestProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "apicurio.metrics.rest.path-filter-pattern", "/apis/registry/v3/.*"
            );
        }
    }

    @Test
    public void test() {

        given()
                .when().get(baseURI + "/apis/registry/v3/groups")
                .then()
                .log().all()
                .statusCode(200);

        given()
                .when().get(baseURI + "/apis/ccompat/v7/subjects")
                .then()
                .log().all()
                .statusCode(200);

        // NOTE: This relies on a specific tag ordering. If this starts to fail, figure out something better.

        lineDoesMatch(".*method=\"\\(unspecified\\)\",path=\"\\(unspecified\\)\",status_code_group=\"1xx\".*0\\.0");
        lineDoesMatch(".*method=\"GET\",path=\"/apis/registry/v3/groups\",status_code_group=\"2xx\".*1\\.0");
        lineDoesNotMatch(".*method=\"GET\",path=\"/apis/ccompat/v7/subjects\",status_code_group=\"2xx\".*1\\.0");
        lineDoesMatch(".*method=\"\\(unspecified\\)\",path=\"\\(unspecified\\)\",status_code_group=\"3xx\".*0\\.0");
        lineDoesMatch(".*method=\"\\(unspecified\\)\",path=\"\\(unspecified\\)\",status_code_group=\"401\".*0\\.0");
        lineDoesMatch(".*method=\"\\(unspecified\\)\",path=\"\\(unspecified\\)\",status_code_group=\"4xx\".*0\\.0");
        lineDoesMatch(".*method=\"\\(unspecified\\)\",path=\"\\(unspecified\\)\",status_code_group=\"5xx\".*0\\.0");
    }
}
