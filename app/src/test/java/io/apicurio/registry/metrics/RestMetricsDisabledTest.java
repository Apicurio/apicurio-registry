package io.apicurio.registry.metrics;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;

@QuarkusTest
@TestProfile(RestMetricsDisabledTest.RestMetricsDisabledTestProfile.class)
public class RestMetricsDisabledTest extends AbstractRestMetricsTest {

    public static class RestMetricsDisabledTestProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "apicurio.metrics.rest.enabled", "false"
            );
        }
    }

    @Test
    public void testDisabled() {

        // Do some requests...
        given()
                .when().get(baseURI + "/apis/registry/v3/groups")
                .then()
                .log().all()
                .statusCode(200);

        lineDoesNotMatch(".*rest_requests_seconds.*");
    }
}
