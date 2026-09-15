package io.apicurio.registry.noprofile.mcpregistry.rest.v0;

import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(McpRegistryListRegressionTest.SmallPageProfile.class)
public class McpRegistryListRegressionTest extends AbstractResourceTestBase {

    private static final String BASE = "/mcp-registry/v0.1";
    private static final String META = "_meta.'io.modelcontextprotocol.registry/official'";

    public static class SmallPageProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("apicurio.features.experimental.enabled", "true",
                    "apicurio.mcp-registry.enabled", "true",
                    "apicurio.mcp-registry.max-page-size", "2");
        }
    }

    private String namespace() {
        return "io.github.list" + UUID.randomUUID().toString().replace("-", "");
    }

    private ValidatableResponse publish(String name, String version, String description) {
        return given().contentType(CT_JSON)
                .body(Map.of("name", name, "version", version, "description", description))
                .post(BASE + "/publish").then().statusCode(200);
    }

    @Test
    public void omittedLimitIsCappedOnBothListEndpoints() {
        String ns = namespace();
        for (int i = 1; i <= 3; i++) {
            publish(ns + "/server" + i, "1.0.0", "server");
        }
        publish(ns + "/server1", "2.0.0", "second version");
        publish(ns + "/server1", "3.0.0", "third version");

        String cursor = given().queryParam("search", ns).get(BASE + "/servers").then()
                .statusCode(200).body("servers", hasSize(2)).body("metadata.count", equalTo(2))
                .body("servers.name", equalTo(List.of(ns + "/server1", ns + "/server2")))
                .extract().path("metadata.nextCursor");
        given().queryParam("search", ns).queryParam("cursor", cursor).get(BASE + "/servers").then()
                .statusCode(200).body("servers.name", equalTo(List.of(ns + "/server3")))
                .body("metadata.count", equalTo(1)).body("metadata.nextCursor", nullValue());

        String versions = BASE + "/servers/" + ns + "/server1/versions";
        cursor = given().get(versions).then().statusCode(200)
                .body("servers.version", equalTo(List.of("1.0.0", "2.0.0")))
                .body("metadata.count", equalTo(2)).extract().path("metadata.nextCursor");
        given().queryParam("cursor", cursor).get(versions).then().statusCode(200)
                .body("servers.version", equalTo(List.of("3.0.0")))
                .body("metadata.count", equalTo(1)).body("metadata.nextCursor", nullValue());
    }

    @Test
    public void searchMatchesNameOrDescriptionBeforePagination() {
        String ns = namespace();
        String term = "weather" + UUID.randomUUID().toString().replace("-", "");
        publish(ns + "/a-unrelated", "1.0.0", "No matching description");
        publish(ns + "/b-" + term, "1.0.0", "Name only");
        publish(ns + "/c-description", "1.0.0", "Forecasts " + term);
        publish(ns + "/d-unrelated", "1.0.0", "No matching description");
        publish(ns + "/e-" + term, "1.0.0", "Both " + term);

        String cursor = given().queryParam("search", term).get(BASE + "/servers").then()
                .statusCode(200).body("metadata.count", equalTo(2))
                .body("servers.name", equalTo(List.of(ns + "/b-" + term, ns + "/c-description")))
                .extract().path("metadata.nextCursor");
        given().queryParam("search", term).queryParam("cursor", cursor).get(BASE + "/servers").then()
                .statusCode(200).body("metadata.count", equalTo(1))
                .body("servers.name", equalTo(List.of(ns + "/e-" + term)))
                .body("metadata.nextCursor", nullValue());
    }

    @Test
    public void searchUsesTheRequestedVersionsDescription() {
        String ns = namespace();
        String term = "historical" + UUID.randomUUID().toString().replace("-", "");
        publish(ns + "/server", "1.0.0", term);
        publish(ns + "/server", "2.0.0", "Replacement description");

        given().queryParam("search", term).get(BASE + "/servers").then().statusCode(200)
                .body("servers", hasSize(0)).body("metadata.count", equalTo(0))
                .body("metadata.nextCursor", nullValue());
        given().queryParam("search", term).queryParam("version", "1.0.0").get(BASE + "/servers")
                .then().statusCode(200).body("servers.name", equalTo(List.of(ns + "/server")))
                .body("servers.version", equalTo(List.of("1.0.0"))).body("metadata.count", equalTo(1));
    }

    @Test
    public void incrementalPagesFollowVersionUpdatesRatherThanNameOrder() {
        String ns = namespace();
        String older = publish(ns + "/z-server", "1.0.0", "First publish")
                .extract().path(META + ".updatedAt");
        String newer = publish(ns + "/a-server", "1.0.0", "Second publish")
                .extract().path(META + ".updatedAt");
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertTrue(Instant.now().toEpochMilli() > Instant.parse(newer).toEpochMilli()));
        given().contentType(CT_JSON).body(Map.of("status", "deprecated"))
                .patch(BASE + "/servers/" + ns + "/z-server/versions/1.0.0/status")
                .then().statusCode(200);

        String cursor = given().queryParam("search", ns).queryParam("updated_since", older)
                .queryParam("limit", 1).get(BASE + "/servers").then().statusCode(200)
                .body("servers.name", equalTo(List.of(ns + "/z-server")))
                .body("metadata.count", equalTo(1)).extract().path("metadata.nextCursor");
        given().queryParam("search", ns).queryParam("updated_since", older).queryParam("limit", 1)
                .queryParam("cursor", cursor).get(BASE + "/servers").then().statusCode(200)
                .body("servers.name", equalTo(List.of(ns + "/a-server")))
                .body("metadata.count", equalTo(1)).body("metadata.nextCursor", nullValue());
    }

    @Test
    public void invalidWatermarkIsRejected() {
        given().queryParam("updated_since", "not-a-timestamp").get(BASE + "/servers").then()
                .statusCode(400).body("error", equalTo("'updated_since' must be an RFC 3339 timestamp"));
    }

    @Test
    public void incrementalListIncludesVersionStatusChangesAfterWatermark() {
        String ns = namespace();
        publish(ns + "/a-unchanged", "1.0.0", "Unchanged");
        String published = publish(ns + "/z-changed", "1.0.0", "Changed")
                .extract().path(META + ".updatedAt");
        // Ensure the watermark is strictly newer than both publishes, without assuming clock resolution.
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertTrue(Instant.now().toEpochMilli() > Instant.parse(published).toEpochMilli()));
        String watermark = Instant.ofEpochMilli(System.currentTimeMillis()).toString();
        String changed = given().contentType(CT_JSON).body(Map.of("status", "deprecated"))
                .patch(BASE + "/servers/" + ns + "/z-changed/versions/1.0.0/status")
                .then().statusCode(200).extract().path(META + ".updatedAt");

        given().queryParam("search", ns).queryParam("updated_since", watermark).queryParam("limit", 1)
                .get(BASE + "/servers").then().statusCode(200)
                .body("servers.name", equalTo(List.of(ns + "/z-changed")))
                .body("servers[0]." + META + ".updatedAt", equalTo(changed))
                .body("servers[0]." + META + ".status", equalTo("deprecated"))
                .body("metadata.count", equalTo(1)).body("metadata.nextCursor", nullValue());

        given().queryParam("search", ns).queryParam("updated_since", changed).get(BASE + "/servers")
                .then().statusCode(200).body("servers.name", equalTo(List.of(ns + "/z-changed")))
                .body("metadata.count", equalTo(1));

        given().queryParam("search", ns).queryParam("updated_since", Instant.parse(changed).plusSeconds(1).toString())
                .get(BASE + "/servers").then().statusCode(200).body("servers", hasSize(0))
                .body("metadata.count", equalTo(0)).body("metadata.nextCursor", nullValue());
    }
}
