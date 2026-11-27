package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With opaque ETags, the default in the {@code prod} profile, the registry sends a hashed ETag. A conditional
 * request carrying that ETag must be answered with 304, so If-None-Match is compared with the hashed form (#11207).
 */
@QuarkusTest
@TestProfile(OpaqueETagTest.OpaqueETagsProfile.class)
public class OpaqueETagTest extends AbstractResourceTestBase {

    public static class OpaqueETagsProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("apicurio.http-caching.enabled", "true",
                    "apicurio.http-caching.opaque-etags-enabled", "true");
        }
    }

    private static final String SCHEMA = """
            {"type": "record", "name": "Opaque", "fields": []}
            """;

    @Test
    public void conditionalRequestWithHashedETagReturns304() throws Exception {
        String groupId = TestUtils.generateGroupId();
        long globalId = createArtifact(groupId, "opaque", ArtifactType.AVRO, SCHEMA, ContentTypes.APPLICATION_JSON)
                .getVersion().getGlobalId();

        String coordinates = "/registry/v3/groups/" + groupId + "/artifacts/opaque/versions/";
        for (String path : List.of(coordinates + "1/content", coordinates + "branch=latest/content",
                "/registry/v3/ids/globalIds/" + globalId)) {
            Response response = given().get(path);
            assertEquals(200, response.getStatusCode(), path + ": " + response.asString());
            String eTag = response.getHeader("ETag");
            // A SHA-256 hex digest, not the raw "key=value;..." form
            assertTrue(eTag.matches("\"[0-9a-f]{64}\""), path + ": " + eTag);

            assertEquals(304, given().header("If-None-Match", eTag).get(path).getStatusCode(), path);
            assertEquals(200, given().header("If-None-Match", "\"" + "0".repeat(64) + "\"").get(path)
                    .getStatusCode(), path);
        }
    }
}
