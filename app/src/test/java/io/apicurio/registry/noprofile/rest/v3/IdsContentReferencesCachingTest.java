package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Content-ID and content-hash lookups with HTTP caching and artifact deletion enabled (#11134): the cache entry
 * varies by the {@code references} parameter, and orphaned content cannot be converted.
 */
@QuarkusTest
@TestProfile(IdsContentReferencesCachingTest.CachingProfile.class)
public class IdsContentReferencesCachingTest extends AbstractIdsContentReferencesTest {

    /** Enables HTTP caching and artifact deletion. */
    public static class CachingProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("apicurio.http-caching.enabled", "true",
                    "apicurio.rest.deletion.artifact.enabled", "true");
        }
    }

    @Test
    public void cacheEntryVariesByReferencesParameter() {
        for (String path : List.of(byId(), byHash())) {
            String preserveTag = get().get(path).then().statusCode(200).extract().header("ETag");
            String derefTag = get().queryParam("references", "DEREFERENCE").get(path).then().statusCode(200)
                    .extract().header("ETag");
            Assertions.assertNotNull(preserveTag, path);
            Assertions.assertNotNull(derefTag, path);
            // A cached PRESERVE response must not satisfy a DEREFERENCE request, and vice versa...
            get().header("If-None-Match", preserveTag).queryParam("references", "DEREFERENCE").get(path)
                    .then().statusCode(200);
            get().header("If-None-Match", derefTag).get(path).then().statusCode(200);
            // ...while each one still validates its own cached response.
            get().header("If-None-Match", derefTag).queryParam("references", "DEREFERENCE").get(path)
                    .then().statusCode(304);
            get().header("If-None-Match", preserveTag).get(path).then().statusCode(304);
        }
    }

    @Test
    public void orphanedContentCannotBeConvertedButIsStillReturnedAsStored() throws Exception {
        // Content used by one artifact only: once that artifact is deleted, no version uses it any more.
        String orphanGroup = TestUtils.generateGroupId();
        CreateArtifactResponse created = createArtifactWithReferences(orphanGroup, "Orphan", ArtifactType.OPENAPI,
                withRefContent.replace("Widget", "OrphanWidget"), ContentTypes.APPLICATION_JSON,
                List.of(ArtifactReference.builder().name("./referenced-types.json#/components/schemas/Widget")
                        .groupId(group).artifactId("ReferencedTypes").version("1").build()));
        long orphanId = created.getVersion().getContentId();
        String orphanPath = "/registry/v3/ids/contentIds/" + orphanId;
        // Not requested with DEREFERENCE before the delete: the storage cache of content and artifact type
        // (ContentCacheDecorator) would then keep serving it. Relies on deleted content not being cleaned up
        // immediately (no orphan cleanup in this profile).
        clientV3.groups().byGroupId(orphanGroup).artifacts().byArtifactId("Orphan").delete();

        String stored = get().get(orphanPath).then().statusCode(200).extract().asString();
        Assertions.assertTrue(stored.contains("OrphanWidget"), stored);
        get().queryParam("references", "DEREFERENCE").get(orphanPath).then().statusCode(404);
    }
}
