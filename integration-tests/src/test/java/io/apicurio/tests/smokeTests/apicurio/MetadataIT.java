package io.apicurio.tests.smokeTests.apicurio;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.hamcrest.number.OrderingComparison;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.EditableArtifactMetaData;
import io.apicurio.registry.rest.client.models.EditableVersionMetaData;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.utils.Constants;
import io.quarkus.test.junit.QuarkusIntegrationTest;

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

        ByteArrayInputStream artifactData = new ByteArrayInputStream(artifactDefinition.getBytes(StandardCharsets.UTF_8));
        VersionMetaData metaData = createArtifact(groupId, artifactId, ArtifactType.AVRO, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData);

        ArtifactMetaData artifactMetaData = registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
        LOGGER.info("Got metadata of artifact with ID {}: {}", artifactId, artifactMetaData);

        assertThat(artifactMetaData.getCreatedOn().toInstant().toEpochMilli(), OrderingComparison.greaterThan(0L));
        assertThat(artifactMetaData.getModifiedOn().toInstant().toEpochMilli(), OrderingComparison.greaterThan(0L));
        assertThat(artifactMetaData.getArtifactId(), is(artifactId));
        assertThat(artifactMetaData.getType(), is("AVRO"));

        EditableArtifactMetaData emd = new EditableArtifactMetaData();

        emd.setName("Artifact Updated Name");
        emd.setDescription("The description of the artifact.");

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(emd);

        retryOp((rc) -> {
            ArtifactMetaData amd = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
            LOGGER.info("Got metadata of artifact with ID {}: {}", artifactId, amd);

            assertThat(amd.getArtifactId(), is(artifactId));
            assertThat(amd.getType(), is("AVRO"));
            assertThat(amd.getDescription(), is("The description of the artifact."));
            assertThat(amd.getName(), is("Artifact Updated Name"));
        });
    }

    @Test
    void getAndUpdateMetadataOfArtifactSpecificVersion() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        String artifactDefinition = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        ByteArrayInputStream artifactData = new ByteArrayInputStream(artifactDefinition.getBytes(StandardCharsets.UTF_8));
        VersionMetaData metaData = createArtifact(groupId, artifactId, ArtifactType.AVRO, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData);

        String artifactUpdateDefinition = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}";
        ByteArrayInputStream artifactUpdateData = new ByteArrayInputStream(artifactUpdateDefinition.getBytes(StandardCharsets.UTF_8));

        metaData = createArtifactVersion(groupId, artifactId, artifactUpdateData);
        LOGGER.info("Artifact with ID {} was updated: {}", artifactId, metaData);

        retryOp((rc) -> rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("2").meta().get());

        VersionMetaData versionMetaData = registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("2").meta().get();

        LOGGER.info("Got metadata of artifact with ID {}: {}", artifactId, versionMetaData);

        assertThat(versionMetaData.getVersion(), is("2"));
        assertThat(versionMetaData.getType(), is("AVRO"));

        EditableVersionMetaData emd = new EditableVersionMetaData();
        emd.setName("Artifact Updated Name");
        emd.setDescription("The description of the artifact.");

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("2").meta().put(emd);

        retryOp((rc) -> {
            ArtifactMetaData artifactMetaData = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
            LOGGER.info("Got metadata of artifact with ID {}: {}", artifactId, artifactMetaData);
            assertThat(artifactMetaData.getType(), is("AVRO"));
            assertThat(artifactMetaData.getName(), is("Artifact Updated Name"));
            assertThat(artifactMetaData.getDescription(), is("The description of the artifact."));
            assertThat(artifactMetaData.getModifiedOn(), notNullValue());
        });

        versionMetaData = registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("1").meta().get();

        LOGGER.info("Got metadata of artifact with ID {} version 1: {}", artifactId, versionMetaData);
        assertThat(versionMetaData.getVersion(), is("1"));
        assertThat(versionMetaData.getType(), is("AVRO"));
        assertThat(versionMetaData.getName(), is("myrecord1"));
        assertThat(versionMetaData.getDescription(),  nullValue());
    }
}
