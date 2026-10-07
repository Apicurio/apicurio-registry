package io.apicurio.registry.noprofile.rest.v2;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.v2.models.ArtifactContent;
import io.apicurio.registry.rest.client.v2.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.v2.models.ArtifactSearchResults;
import io.apicurio.registry.rest.client.v2.models.IfExists;
import io.apicurio.registry.rest.client.v2.models.VersionMetaData;
import io.apicurio.registry.rest.client.v2.models.VersionSearchResults;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests that the v2 Java client SDK correctly parses date fields when the server
 * is configured with the legacy (non-ISO-8601 compliant) date format
 * (yyyy-MM-dd'T'HH:mm:ssZ, e.g. "2025-10-29T21:54:37+0000").
 *
 * This originally documented the parsing failure reported in issue #6799. That bug
 * has since been fixed via a fallback parser (see DateTimeUtil.getOffsetDateTimeValue
 * and the post-process-kiota build step in java-sdk/client-v2/pom.xml), so these
 * tests now assert that legacy-format dates parse successfully, not that they fail.
 *
 * Each date assertion checks the parsed value falls within a time window captured
 * immediately before/after the call, rather than just asserting non-null — this
 * catches a parser that returns a non-null but wrong instant (e.g. wrong epoch or
 * timezone offset), which a bare assertNotNull would miss. The legacy format has
 * second precision (no milliseconds), so the window is truncated to seconds and
 * padded by a few seconds of tolerance for request/processing latency.
 */
@QuarkusTest
@TestProfile(LegacyV2ApiDateFormatTest.LegacyV2DateFormatTestProfile.class)
class LegacyV2ApiDateFormatTest extends AbstractResourceTestBase {

    private static final long TOLERANCE_SECONDS = 10;

    public static class LegacyV2DateFormatTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("apicurio.apis.date-format", "yyyy-MM-dd'T'HH:mm:ssZ");
        }
    }

    private static OffsetDateTime windowStart() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).minusSeconds(TOLERANCE_SECONDS);
    }

    private static OffsetDateTime windowEnd() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).plusSeconds(TOLERANCE_SECONDS);
    }

    private static void assertWithinWindow(OffsetDateTime actual, OffsetDateTime before, OffsetDateTime after, String message) {
        assertNotNull(actual, message + " (was null)");
        assertFalse(actual.isBefore(before), message + " (parsed value " + actual + " is before window start " + before + ")");
        assertFalse(actual.isAfter(after), message + " (parsed value " + actual + " is after window end " + after + ")");
    }

    @Test
    void testCreateArtifactParsesLegacyDateFormat() {
        String groupId = TestUtils.generateGroupId();
        String artifactContentString = resourceToString("openapi-empty.json");
        String artifactId = "testCreateArtifact";

        ArtifactContent artifactContent = new ArtifactContent();
        artifactContent.setContent(artifactContentString);

        OffsetDateTime before = windowStart();
        ArtifactMetaData metadata = assertDoesNotThrow(() ->
                clientV2.groups()
                        .byGroupId(groupId)
                        .artifacts()
                        .post(artifactContent, requestConfig -> {
                            requestConfig.headers.add("X-Registry-ArtifactId", artifactId);
                            requestConfig.headers.add("X-Registry-ArtifactType", "OPENAPI");
                            requestConfig.queryParameters.ifExists = IfExists.FAIL;
                        }),
                "Creating an artifact should succeed with the legacy date format enabled");
        OffsetDateTime after = windowEnd();

        assertWithinWindow(metadata.getCreatedOn(), before, after,
                "createdOn should reflect the actual creation time despite the legacy date format");
    }

    @Test
    void testGetArtifactMetadataParsesLegacyDateFormat() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "testGetArtifactMetadata";

        OffsetDateTime before = windowStart();
        createArtifact(groupId, artifactId);

        ArtifactMetaData metadata = assertDoesNotThrow(() ->
                clientV2.groups()
                        .byGroupId(groupId)
                        .artifacts()
                        .byArtifactId(artifactId)
                        .meta()
                        .get(),
                "Getting artifact metadata should succeed with the legacy date format enabled");
        OffsetDateTime after = windowEnd();

        assertWithinWindow(metadata.getCreatedOn(), before, after,
                "createdOn should reflect the actual creation time despite the legacy date format");
        assertWithinWindow(metadata.getModifiedOn(), before, after,
                "modifiedOn should reflect the actual modification time despite the legacy date format");
    }

    @Test
    void testGetVersionMetadataParsesLegacyDateFormat() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "testGetVersionMetadata";

        OffsetDateTime before = windowStart();
        createArtifact(groupId, artifactId);

        VersionMetaData versionMetadata = assertDoesNotThrow(() ->
                clientV2.groups()
                        .byGroupId(groupId)
                        .artifacts()
                        .byArtifactId(artifactId)
                        .versions()
                        .byVersion("1")
                        .meta()
                        .get(),
                "Getting version metadata should succeed with the legacy date format enabled");
        OffsetDateTime after = windowEnd();

        assertWithinWindow(versionMetadata.getCreatedOn(), before, after,
                "createdOn should reflect the actual creation time despite the legacy date format");
    }

    @Test
    void testListVersionsParsesLegacyDateFormat() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "testListVersions";

        OffsetDateTime before = windowStart();
        createArtifact(groupId, artifactId);

        VersionSearchResults results = assertDoesNotThrow(() ->
                clientV2.groups()
                        .byGroupId(groupId)
                        .artifacts()
                        .byArtifactId(artifactId)
                        .versions()
                        .get(),
                "Listing versions should succeed with the legacy date format enabled");
        OffsetDateTime after = windowEnd();

        assertEquals(1, results.getVersions().size(), "Expected exactly one version");
        assertWithinWindow(results.getVersions().get(0).getCreatedOn(), before, after,
                "createdOn should reflect the actual creation time despite the legacy date format");
    }

    @Test
    void testSearchArtifactsParsesLegacyDateFormat() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "testSearchArtifacts";

        OffsetDateTime before = windowStart();
        createArtifact(groupId, artifactId);

        ArtifactSearchResults results = assertDoesNotThrow(() ->
                clientV2.search()
                        .artifacts()
                        .get(requestConfig -> {
                            requestConfig.queryParameters.group = groupId;
                        }),
                "Searching artifacts should succeed with the legacy date format enabled");
        OffsetDateTime after = windowEnd();

        assertEquals(1, results.getArtifacts().size(), "Expected exactly one search result");
        assertWithinWindow(results.getArtifacts().get(0).getCreatedOn(), before, after,
                "createdOn should reflect the actual creation time despite the legacy date format");
    }

    private void createArtifact(String groupId, String artifactId) {
        try {
            String artifactContent = resourceToString("openapi-empty.json");
            createArtifact(groupId, artifactId, io.apicurio.registry.types.ArtifactType.OPENAPI,
                    artifactContent, io.apicurio.registry.types.ContentTypes.APPLICATION_JSON, null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create artifact", e);
        }
    }
}