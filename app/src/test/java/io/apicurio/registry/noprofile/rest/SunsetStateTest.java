package io.apicurio.registry.noprofile.rest;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.client.models.VersionState;
import io.apicurio.registry.rest.client.models.WrappedVersionState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@QuarkusTest
@Tag(ApicurioTestTags.SLOW)
public class SunsetStateTest extends AbstractResourceTestBase {

    @Test
    public void testSunsetTransitionFromDeprecated() throws Exception {
        String groupId = "SunsetStateTest_fromDeprecated";
        String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.JSON, "{\"type\": \"string\"}",
                ContentTypes.APPLICATION_JSON);

        setState(groupId, artifactId, "1", VersionState.DEPRECATED);
        assertState(groupId, artifactId, "1", VersionState.DEPRECATED);

        setState(groupId, artifactId, "1", VersionState.SUNSET);
        assertState(groupId, artifactId, "1", VersionState.SUNSET);
    }

    @Test
    public void testSunsetTransitionBackToEnabled() throws Exception {
        String groupId = "SunsetStateTest_backToEnabled";
        String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.JSON, "{\"type\": \"string\"}",
                ContentTypes.APPLICATION_JSON);

        setState(groupId, artifactId, "1", VersionState.DEPRECATED);
        setState(groupId, artifactId, "1", VersionState.SUNSET);
        setState(groupId, artifactId, "1", VersionState.ENABLED);
        assertState(groupId, artifactId, "1", VersionState.ENABLED);
    }

    @Test
    public void testSunsetFromEnabled() throws Exception {
        String groupId = "SunsetStateTest_fromEnabled";
        String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.JSON, "{\"type\": \"string\"}",
                ContentTypes.APPLICATION_JSON);

        setState(groupId, artifactId, "1", VersionState.SUNSET);
        assertState(groupId, artifactId, "1", VersionState.SUNSET);
    }

    private void setState(String groupId, String artifactId, String version, VersionState state) {
        WrappedVersionState vs = new WrappedVersionState();
        vs.setState(state);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression(version).state().put(vs);
    }

    private void assertState(String groupId, String artifactId, String version, VersionState expected) {
        VersionMetaData md = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression(version).get();
        Assertions.assertEquals(expected, md.getState());
    }
}
