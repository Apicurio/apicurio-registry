package io.apicurio.registry.metrics;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static io.restassured.RestAssured.given;

@QuarkusTest
@TestProfile(RestMetricsDefaultsTest.DummyTestProfile.class)
public class RestMetricsDefaultsTest extends AbstractRestMetricsTest {

    // This is intentional to force a fresh restart of Registry and avoid sharing with other tests.
    public static class DummyTestProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "e1078b5a-5936-461e-8e38-bee364c0e04f", "774d6062-0d8d-4646-835b-5643967f7675"
            );
        }
    }

    private static final Logger log = LoggerFactory.getLogger(RestMetricsDefaultsTest.class);

    @Test
    public void test() {

        // NOTE: This relies on a specific tag ordering. If this starts to fail, figure out something better.

        lineDoesMatch(".*method=\"\\(unspecified\\)\",path=\"\\(unspecified\\)\",status_code_group=\"1xx\".*0\\.0");
        lineDoesMatch(".*method=\"\\(unspecified\\)\",path=\"\\(unspecified\\)\",status_code_group=\"2xx\".*0\\.0");
        lineDoesMatch(".*method=\"\\(unspecified\\)\",path=\"\\(unspecified\\)\",status_code_group=\"3xx\".*0\\.0");
        lineDoesMatch(".*method=\"\\(unspecified\\)\",path=\"\\(unspecified\\)\",status_code_group=\"401\".*0\\.0");
        lineDoesMatch(".*method=\"\\(unspecified\\)\",path=\"\\(unspecified\\)\",status_code_group=\"4xx\".*0\\.0");
        lineDoesMatch(".*method=\"\\(unspecified\\)\",path=\"\\(unspecified\\)\",status_code_group=\"5xx\".*0\\.0");

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

        metrics(true);

        lineDoesMatch(".*method=\"\\(unspecified\\)\",path=\"\\(unspecified\\)\",status_code_group=\"1xx\".*0\\.0");
        lineDoesMatch(".*method=\"GET\",path=\"/apis/registry/v3/groups\",status_code_group=\"2xx\".*1\\.0");
        lineDoesMatch(".*method=\"GET\",path=\"/apis/ccompat/v7/subjects\",status_code_group=\"2xx\".*1\\.0");
        lineDoesMatch(".*method=\"\\(unspecified\\)\",path=\"\\(unspecified\\)\",status_code_group=\"3xx\".*0\\.0");
        lineDoesMatch(".*method=\"\\(unspecified\\)\",path=\"\\(unspecified\\)\",status_code_group=\"401\".*0\\.0");
        lineDoesMatch(".*method=\"\\(unspecified\\)\",path=\"\\(unspecified\\)\",status_code_group=\"4xx\".*0\\.0");
        lineDoesMatch(".*method=\"\\(unspecified\\)\",path=\"\\(unspecified\\)\",status_code_group=\"5xx\".*0\\.0");
    }
}
