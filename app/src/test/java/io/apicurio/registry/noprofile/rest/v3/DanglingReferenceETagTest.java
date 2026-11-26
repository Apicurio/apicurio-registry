package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With HTTP caching and version mutability enabled, the ETag of converted content covers the content IDs
 * of the whole reference tree. A reference to a version that does not exist must be skipped there, as it is
 * by {@code references=REWRITE} itself, not fail the request with a 404 (#9626).
 */
@QuarkusTest
@TestProfile(DanglingReferenceETagTest.CachingProfile.class)
public class DanglingReferenceETagTest extends AbstractResourceTestBase {

    /** Enables HTTP caching and version mutability, the conditions for the reference-tree ETag. */
    public static class CachingProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("apicurio.http-caching.enabled", "true",
                    "apicurio.rest.mutability.artifact-version-content.enabled", "true");
        }
    }

    private static final String JSON_ORDER = """
            {"$schema": "http://json-schema.org/draft-07/schema#", "type": "object",
             "properties": {"total": {"$ref": "money.json"}}}
            """;

    @Inject
    @Current
    RegistryStorage storage;

    @Test
    public void rewriteWithADanglingReferenceReturnsContentAndAnETag() throws Exception {
        String groupId = TestUtils.generateGroupId();
        // money@1 is never created: the only reference is dangling
        CreateArtifactResponse created = createArtifactWithReferences(groupId, "order", ArtifactType.JSON,
                JSON_ORDER, ContentTypes.APPLICATION_JSON, List.of(ArtifactReference.builder().groupId(groupId)
                        .artifactId("money").version("1").name("money.json").build()));
        long globalId = created.getVersion().getGlobalId();
        long contentId = created.getVersion().getContentId();
        String contentHash = storage.getContentById(contentId).getContentHash();

        for (String path : List.of("/registry/v3/groups/" + groupId + "/artifacts/order/versions/1/content",
                "/registry/v3/ids/globalIds/" + globalId, "/registry/v3/ids/contentIds/" + contentId,
                "/registry/v3/ids/contentHashes/" + contentHash)) {
            Response response = given().queryParam("references", "REWRITE").get(path);

            assertEquals(200, response.getStatusCode(), path + ": " + response.asString());
            assertTrue(response.asString().contains("/artifacts/money/versions/1/content?references=REWRITE"),
                    path + ": " + response.asString());
            String eTag = response.getHeader("ETag");
            assertNotNull(eTag, path);
            given().header("If-None-Match", eTag).queryParam("references", "REWRITE").get(path).then()
                    .statusCode(304);
        }
    }
}
