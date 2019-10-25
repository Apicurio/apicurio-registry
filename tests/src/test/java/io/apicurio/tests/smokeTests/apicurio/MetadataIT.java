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

import io.apicurio.tests.BaseIT;
import io.apicurio.tests.utils.subUtils.ArtifactUtils;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

class MetadataIT extends BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataIT.class);

    @Test
    void getAndUpdateMetadataOfArtifact() {
        Schema artifact = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}");
        Response response = ArtifactUtils.createArtifact(artifact.toString());
        String artifactId = response.jsonPath().getString("id");
        LOGGER.info("Created artifact {} with ID {}", "myrecord1", artifactId);

        response = ArtifactUtils.getArtifactMetadata(artifactId);
        JsonPath jsonPath = response.jsonPath();
        LOGGER.info("Got metadata of artifact with ID {}: {}", artifactId, jsonPath.get());
        assertThat(jsonPath.get("version"), is(1));
        assertThat(jsonPath.get("type"), is("AVRO"));

        String metadata = "{\"name\": \"Artifact Name\",\"description\": \"The description of the artifact.\"}";
        ArtifactUtils.updateArtifactMetadata(artifactId, metadata);

        response = ArtifactUtils.getArtifactMetadata(artifactId);
        jsonPath = response.jsonPath();
        LOGGER.info("Got metadata of artifact with ID {}: {}", artifactId, jsonPath.get());
        assertThat(jsonPath.get("version"), is(1));
        assertThat(jsonPath.get("type"), is("AVRO"));
        assertThat(jsonPath.get("name"), is("Artifact Name"));
        assertThat(jsonPath.get("description"), is("The description of the artifact."));
    }

    @Test
    void getAndUpdateMetadataOfArtifactSpecificVersion() {
        Schema artifact = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}");
        Response response = ArtifactUtils.createArtifact(artifact.toString());
        String artifactId = response.jsonPath().getString("id");
        LOGGER.info("Created artifact {} with ID {}", "myrecord1", artifactId);

        Schema updatedArtifact = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        ArtifactUtils.updateArtifact(artifactId, updatedArtifact.toString());

        response = ArtifactUtils.getArtifactVersionMetadata(artifactId, "2");
        JsonPath jsonPath = response.jsonPath();
        LOGGER.info("Got metadata of artifact with ID {}: {}", artifactId, jsonPath.get());
        assertThat(jsonPath.get("version"), is(2));
        assertThat(jsonPath.get("type"), is("AVRO"));

        String metadata = "{\"name\": \"Artifact Name\",\"description\": \"The description of the artifact.\"}";
        ArtifactUtils.updateArtifactMetadata(artifactId, metadata);

        response = ArtifactUtils.getArtifactMetadata(artifactId);
        jsonPath = response.jsonPath();
        LOGGER.info("Got metadata of artifact with ID {}: {}", artifactId, jsonPath.get());
        assertThat(jsonPath.get("version"), is(2));
        assertThat(jsonPath.get("type"), is("AVRO"));
        assertThat(jsonPath.get("name"), is("Artifact Name"));
        assertThat(jsonPath.get("description"), is("The description of the artifact."));
        assertThat(jsonPath.get("modifiedOn"),  notNullValue());

        ArtifactUtils.deleteArtifactVersionMetadata(artifactId, "2");
        response = ArtifactUtils.getArtifactVersionMetadata(artifactId, "2");
        jsonPath = response.jsonPath();
        LOGGER.info("Got metadata of artifact with ID {} version 2: {}", artifactId, jsonPath.get());
        assertThat(jsonPath.get("version"), is(2));
        assertThat(jsonPath.get("type"), is("AVRO"));
        assertThat(jsonPath.get("name"), nullValue());
        assertThat(jsonPath.get("description"),  nullValue());
        assertThat(jsonPath.get("modifiedOn"),  nullValue());
    }
}
