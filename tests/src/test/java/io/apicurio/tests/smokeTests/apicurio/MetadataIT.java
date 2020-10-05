/*
 * Copyright 2020 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.tests.smokeTests.apicurio;

import static io.apicurio.tests.Constants.SMOKE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.hamcrest.number.OrderingComparison;
import org.junit.jupiter.api.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.client.RegistryRestClient;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.rest.beans.VersionMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.RegistryRestClientTest;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.BaseIT;
import io.apicurio.tests.utils.subUtils.ArtifactUtils;

@Tag(SMOKE)
class MetadataIT extends BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataIT.class);

    @RegistryRestClientTest
    @Tag(ACCEPTANCE)
    void getAndUpdateMetadataOfArtifact(RegistryRestClient client) throws Exception {
        String artifactId = TestUtils.generateArtifactId();
        String artifactDefinition = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        ByteArrayInputStream artifactData = new ByteArrayInputStream(artifactDefinition.getBytes(StandardCharsets.UTF_8));
        ArtifactMetaData metaData = ArtifactUtils.createArtifact(client, ArtifactType.AVRO, artifactId, artifactData);
        TestUtils.retry(() -> client.getArtifactMetaDataByGlobalId(metaData.getGlobalId()));
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData);

        ArtifactMetaData artifactMetaData = client.getArtifactMetaData(artifactId);
        LOGGER.info("Got metadata of artifact with ID {}: {}", artifactId, artifactMetaData);

        assertThat(artifactMetaData.getCreatedOn(), OrderingComparison.greaterThan(0L));
        assertThat(artifactMetaData.getModifiedOn(), OrderingComparison.greaterThan(0L));
        assertThat(artifactMetaData.getId(), is(artifactId));
        assertThat(artifactMetaData.getVersion(), is(1));
        assertThat(artifactMetaData.getType().value(), is("AVRO"));

        EditableMetaData emd = new EditableMetaData();

        emd.setName("Artifact Updated Name");
        emd.setDescription("The description of the artifact.");

        client.updateArtifactMetaData(artifactId, emd);

        TestUtils.retry(() -> {
            ArtifactMetaData amd = client.getArtifactMetaData(artifactId);
            LOGGER.info("Got metadata of artifact with ID {}: {}", artifactId, amd);

            assertThat(amd.getId(), is(artifactId));
            assertThat(amd.getVersion(), is(1));
            assertThat(amd.getType().value(), is("AVRO"));
            assertThat(amd.getDescription(), is("The description of the artifact."));
            assertThat(amd.getName(), is("Artifact Updated Name"));
        });
    }

    @RegistryRestClientTest
    void getAndUpdateMetadataOfArtifactSpecificVersion(RegistryRestClient client) throws Exception {
        String artifactId = TestUtils.generateArtifactId();
        String artifactDefinition = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        ByteArrayInputStream artifactData = new ByteArrayInputStream(artifactDefinition.getBytes(StandardCharsets.UTF_8));
        ArtifactMetaData metaData = ArtifactUtils.createArtifact(client, ArtifactType.AVRO, artifactId, artifactData);
        ArtifactMetaData amd1 = metaData;
        TestUtils.retry(() -> client.getArtifactMetaDataByGlobalId(amd1.getGlobalId()));
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData);

        String artifactUpdateDefinition = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}";
        ByteArrayInputStream artifactUpdateData = new ByteArrayInputStream(artifactUpdateDefinition.getBytes(StandardCharsets.UTF_8));

        metaData = ArtifactUtils.updateArtifact(client, ArtifactType.AVRO, artifactId, artifactUpdateData);
        LOGGER.info("Artifact with ID {} was updated: {}", artifactId, metaData);
        ArtifactMetaData amd2 = metaData;
        TestUtils.retry(() -> client.getArtifactMetaDataByGlobalId(amd2.getGlobalId()));

        VersionMetaData versionMetaData = client.getArtifactVersionMetaData(artifactId, 2);

        LOGGER.info("Got metadata of artifact with ID {}: {}", artifactId, versionMetaData);

        assertThat(versionMetaData.getVersion(), is(2));
        assertThat(versionMetaData.getType().value(), is("AVRO"));

        EditableMetaData emd = new EditableMetaData();

        emd.setName("Artifact Updated Name");
        emd.setDescription("The description of the artifact.");

        client.updateArtifactVersionMetaData(artifactId, 2, emd);

        TestUtils.retry(() -> {
            ArtifactMetaData artifactMetaData = client.getArtifactMetaData(artifactId);
            LOGGER.info("Got metadata of artifact with ID {}: {}", artifactId, artifactMetaData);
            assertThat(artifactMetaData.getVersion(), is(2));
            assertThat(artifactMetaData.getType().value(), is("AVRO"));
            assertThat(artifactMetaData.getName(), is("Artifact Updated Name"));
            assertThat(artifactMetaData.getDescription(), is("The description of the artifact."));
            assertThat(artifactMetaData.getModifiedOn(), notNullValue());
        });

        versionMetaData = client.getArtifactVersionMetaData(artifactId, 1);

        LOGGER.info("Got metadata of artifact with ID {} version 1: {}", artifactId, versionMetaData);
        assertThat(versionMetaData.getVersion(), is(1));
        assertThat(versionMetaData.getType().value(), is("AVRO"));
        assertThat(versionMetaData.getName(), is("myrecord1"));
        assertThat(versionMetaData.getDescription(),  nullValue());
    }
}
