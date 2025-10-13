package io.apicurio.registry.metrics;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.restassured.RestAssured.given;

@QuarkusTest
public class RestMetricsDefaultsTest extends AbstractRestMetricsTest {

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
