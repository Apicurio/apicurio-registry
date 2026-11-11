package io.apicurio.registry.limits;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

/**
 * The label limits apply on their own, without the property limits (issue #10030).
 */
@QuarkusTest
@TestProfile(LabelLimitsTest.Profile.class)
public class LabelLimitsTest extends AbstractResourceTestBase {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("apicurio.limits.config.max-artifact-labels", "2",
                    "apicurio.limits.config.max-label-size.bytes", "8");
        }
    }

    private String newArtifact() throws Exception {
        String artifactId = "limits-" + UUID.randomUUID();
        createArtifact("default", artifactId, ArtifactType.JSON, "{\"type\":\"object\"}",
                ContentTypes.APPLICATION_JSON);
        return artifactId;
    }

    private io.restassured.response.ValidatableResponse putLabels(String artifactId, Map<String, String> labels) {
        return given().contentType(ContentType.JSON).body(Map.of("labels", labels))
                .put("/registry/v3/groups/default/artifacts/{a}", artifactId).then();
    }

    @Test
    void labelsWithinBothLimitsAreAccepted() throws Exception {
        putLabels(newArtifact(), Map.of("abcd", "efgh", "k", "v")).statusCode(204);
    }

    @Test
    void oneLabelTooManyIsRejected() throws Exception {
        putLabels(newArtifact(), Map.of("a", "1", "b", "2", "c", "3")).statusCode(409)
                .body("detail", containsString("Maximum number of labels exceeded for this artifact (limit: 2, actual: 3)"));
    }

    /** Key plus value: 5 + 4 bytes is over 8, though neither alone is. */
    @Test
    void aLabelOverTheSizeLimitIsRejected() throws Exception {
        putLabels(newArtifact(), Map.of("abcde", "fghi")).statusCode(409)
                .body("detail", containsString("Maximum label size, key plus value, exceeded (limit: 8 bytes)"));
    }

    /** The limit is in bytes: "é" is 2 bytes in UTF-8, so 4 characters make 8 bytes plus the key. */
    @Test
    void theSizeLimitCountsUtf8Bytes() throws Exception {
        putLabels(newArtifact(), Map.of("k", "éééé")).statusCode(409);
        putLabels(newArtifact(), Map.of("k", "ééé")).statusCode(204);
    }
}
