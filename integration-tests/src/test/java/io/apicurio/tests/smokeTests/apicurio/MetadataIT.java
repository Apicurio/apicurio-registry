package io.apicurio.tests.smokeTests.apicurio;

import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.EditableArtifactMetaData;
import io.apicurio.registry.rest.client.models.EditableVersionMetaData;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.utils.Constants;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.hamcrest.number.OrderingComparison;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@Tag(Constants.SMOKE)
@QuarkusIntegrationTest
class MetadataIT extends ApicurioRegistryBaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataIT.class);

    @Test
    @Tag(ACCEPTANCE)
    void getAndUpdateMetadataOfArtifact() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        String artifactDefinition = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        var caResponse = createArtifact(groupId, artifactId, ArtifactType.AVRO, artifactDefinition, ContentTypes.APPLICATION_JSON, null, null);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, caResponse.getArtifact());

        ArtifactMetaData artifactMetaData = registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
        LOGGER.info("Got metadata of artifact with ID {}: {}", artifactId, artifactMetaData);

        assertThat(artifactMetaData.getCreatedOn().toInstant().toEpochMilli(), OrderingComparison.greaterThan(0L));
        assertThat(artifactMetaData.getModifiedOn().toInstant().toEpochMilli(), OrderingComparison.greaterThan(0L));
        assertThat(artifactMetaData.getArtifactId(), is(artifactId));
        assertThat(artifactMetaData.getArtifactType(), is("AVRO"));

        EditableArtifactMetaData emd = new EditableArtifactMetaData();

        emd.setName("Artifact Updated Name");
        emd.setDescription("The description of the artifact.");

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(emd);

        retryOp((rc) -> {
            ArtifactMetaData amd = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
            LOGGER.info("Got metadata of artifact with ID {}: {}", artifactId, amd);

            assertThat(amd.getArtifactId(), is(artifactId));
            assertThat(amd.getArtifactType(), is("AVRO"));
            assertThat(amd.getDescription(), is("The description of the artifact."));
            assertThat(amd.getName(), is("Artifact Updated Name"));
        });
    }

    @Test
    void getAndUpdateMetadataOfArtifactSpecificVersion() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        String artifactDefinition = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        var caResponse = createArtifact(groupId, artifactId, ArtifactType.AVRO, artifactDefinition, ContentTypes.APPLICATION_JSON, null, (ca) -> {
            ca.getFirstVersion().setName("Version 1 Name");
        });
        LOGGER.info("Created artifact {} with metadata {}", artifactId, caResponse.getArtifact());

        String artifactUpdateDefinition = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}";

        var metaData = createArtifactVersion(groupId, artifactId, artifactUpdateDefinition, ContentTypes.APPLICATION_JSON, null);
        LOGGER.info("Artifact with ID {} was updated: {}", artifactId, metaData);

        retryOp((rc) -> rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("2").get());

        VersionMetaData versionMetaData = registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("2").get();

        LOGGER.info("Got metadata of artifact with ID {}: {}", artifactId, versionMetaData);

        assertThat(versionMetaData.getVersion(), is("2"));
        assertThat(versionMetaData.getArtifactType(), is("AVRO"));

        EditableVersionMetaData emd = new EditableVersionMetaData();
        emd.setName("Version 2 Name");
        emd.setDescription("The description of the artifact.");

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("2").put(emd);

        versionMetaData = registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("1").get();

        LOGGER.info("Got metadata of artifact with ID {} version 1: {}", artifactId, versionMetaData);
        assertThat(versionMetaData.getVersion(), is("1"));
        assertThat(versionMetaData.getArtifactType(), is("AVRO"));
        assertThat(versionMetaData.getName(), is("Version 1 Name"));
        assertThat(versionMetaData.getDescription(),  nullValue());
    }
}
