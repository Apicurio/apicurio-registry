/*
 * Copyright 2019 Red Hat
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

import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.rest.beans.VersionMetaData;
import io.apicurio.tests.BaseIT;
import io.apicurio.tests.utils.subUtils.ArtifactUtils;
import org.hamcrest.number.OrderingComparison;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.tests.Constants.SMOKE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.ByteArrayInputStream;
import javax.ws.rs.WebApplicationException;

@Tag(SMOKE)
class MetadataIT extends BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataIT.class);

    @Test
    void getAndUpdateMetadataOfArtifact() {
        String artifactId = "artifactUpdateAndMetadataId";
        String artifactDefinition = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        ByteArrayInputStream artifactData = new ByteArrayInputStream(artifactDefinition.getBytes());
        ArtifactMetaData metaData = ArtifactUtils.createArtifact(apicurioService, artifactId, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData.toString());

        ArtifactMetaData artifactMetaData = apicurioService.getArtifactMetaData(artifactId);
        LOGGER.info("Got metadata of artifact with ID {}: {}", artifactId, artifactMetaData.toString());

        assertThat(artifactMetaData.getCreatedOn(), OrderingComparison.greaterThan(0L));
        assertThat(artifactMetaData.getModifiedOn(), OrderingComparison.greaterThan(0L));
        assertThat(artifactMetaData.getId(), is("artifactUpdateAndMetadataId"));
        assertThat(artifactMetaData.getVersion(), is(1));
        assertThat(artifactMetaData.getType().value(), is("AVRO"));

        EditableMetaData emd = new EditableMetaData();

        emd.setName("Artifact Updated Name");
        emd.setDescription("The description of the artifact.");

        apicurioService.updateArtifactMetaData(artifactId, emd);

        artifactMetaData = apicurioService.getArtifactMetaData(artifactId);
        LOGGER.info("Got metadata of artifact with ID {}: {}", artifactId, artifactMetaData.toString());

        assertThat(artifactMetaData.getId(), is("artifactUpdateAndMetadataId"));
        assertThat(artifactMetaData.getVersion(), is(1));
        assertThat(artifactMetaData.getType().value(), is("AVRO"));
        assertThat(artifactMetaData.getDescription(), is("The description of the artifact."));
        assertThat(artifactMetaData.getName(), is("Artifact Updated Name"));
    }

    @Test
    void getAndUpdateMetadataOfArtifactSpecificVersion() {
        String artifactId = "artifactUpdateMetadataOfArtifactSpecificVersionId";
        String artifactDefinition = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        ByteArrayInputStream artifactData = new ByteArrayInputStream(artifactDefinition.getBytes());
        ArtifactMetaData metaData = ArtifactUtils.createArtifact(apicurioService, artifactId, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData.toString());

        String artifactUpdateDefinition = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}";
        ByteArrayInputStream artifactUpdateData = new ByteArrayInputStream(artifactUpdateDefinition.getBytes());

        metaData = ArtifactUtils.updateArtifact(apicurioService, artifactId, artifactUpdateData);
        LOGGER.info("Artifact with ID {} was updated: {}", artifactId, metaData.toString());

        VersionMetaData versionMetaData = apicurioService.getArtifactVersionMetaData(2, artifactId);

        LOGGER.info("Got metadata of artifact with ID {}: {}", artifactId, versionMetaData.toString());

        assertThat(versionMetaData.getVersion(), is(2));
        assertThat(versionMetaData.getType().value(), is("AVRO"));

        EditableMetaData emd = new EditableMetaData();

        emd.setName("Artifact Updated Name");
        emd.setDescription("The description of the artifact.");

        apicurioService.updateArtifactVersionMetaData(2, artifactId, emd);

        ArtifactMetaData artifactMetaData = apicurioService.getArtifactMetaData(artifactId);
        LOGGER.info("Got metadata of artifact with ID {}: {}", artifactId, artifactMetaData.toString());
        assertThat(artifactMetaData.getVersion(), is(2));
        assertThat(artifactMetaData.getType().value(), is("AVRO"));
        assertThat(artifactMetaData.getName(), is("Artifact Updated Name"));
        assertThat(artifactMetaData.getDescription(), is("The description of the artifact."));
        assertThat(artifactMetaData.getModifiedOn(),  notNullValue());

        apicurioService.deleteArtifactVersion(2, artifactId);

        try {
            apicurioService.getArtifactVersionMetaData(2, artifactId);
        } catch (WebApplicationException e) {
            assertThat("{\"message\":\"No version '2' found for artifact with ID 'artifactUpdateMetadataOfArtifactSpecificVersionId'.\",\"error_code\":404}", is(e.getResponse().readEntity(String.class)));
        }

        versionMetaData = apicurioService.getArtifactVersionMetaData(1, artifactId);

        LOGGER.info("Got metadata of artifact with ID {} version 1: {}", artifactId, versionMetaData.toString());
        assertThat(versionMetaData.getVersion(), is(1));
        assertThat(versionMetaData.getType().value(), is("AVRO"));
        assertThat(versionMetaData.getName(), nullValue());
        assertThat(versionMetaData.getDescription(),  nullValue());
    }
}
