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
import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.types.RuleType;
import io.apicurio.tests.BaseIT;
import io.apicurio.tests.utils.subUtils.ArtifactUtils;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;

import javax.ws.rs.WebApplicationException;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static io.apicurio.tests.Constants.SMOKE;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

@Tag(SMOKE)
class ArtifactsIT extends BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtifactsIT.class);

    @Test
    void createAndUpdateArtifact() {
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("FULL");

        LOGGER.info("Creating global rule:{}", rule.toString());
        apicurioService.createGlobalRule(rule);

        String artifactId = "createAndUpdateArtifactId1";

        ByteArrayInputStream artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}".getBytes());
        ArtifactMetaData metaData = ArtifactUtils.createArtifact(apicurioService, artifactId, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData.toString());

        JsonObject response = new JsonObject(apicurioService.getLatestArtifact(artifactId).readEntity(String.class));

        LOGGER.info("Artifact with name:{} and content:{} was created", response.getString("name"), response.toString());

        String invalidArtifactDefinition = "<type>record</type>\n<name>test</name>";
        artifactData = new ByteArrayInputStream(invalidArtifactDefinition.getBytes());
        String invalidArtifactId = "createAndUpdateArtifactId2";

        try {
            LOGGER.info("Invalid artifact sent {}", invalidArtifactDefinition);
            ArtifactUtils.createArtifact(apicurioService, artifactId, artifactData);
        } catch (WebApplicationException e) {
            assertThat("{\"message\":\"Syntax violation for Avro artifact.\",\"error_code\":400}", is(e.getResponse().readEntity(String.class)));
        }

        artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"long\"}]}".getBytes());
        metaData = ArtifactUtils.updateArtifact(apicurioService, artifactId, artifactData);
        LOGGER.info("Artifact with ID {} was updated: {}", artifactId, metaData.toString());

        response = new JsonObject(apicurioService.getLatestArtifact(artifactId).readEntity(String.class));

        LOGGER.info("Artifact with ID {} was updated: {}", artifactId, response.toString());

        List<Long> apicurioVersions = apicurioService.listArtifactVersions(artifactId);

        LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId, apicurioVersions.toString());
        assertThat(apicurioVersions, hasItems(1L, 2L));

        response = new JsonObject(apicurioService.getArtifactVersion(1, artifactId).readEntity(String.class));

        LOGGER.info("Artifact with ID {} and version {}: {}", artifactId, 1, response.toString());

        assertThat(response.getJsonArray("fields").getJsonObject(0).getString("name"), is("foo"));
    }

    @Test
    void createAndDeleteMultipleArtifacts() {
        LOGGER.info("Creating some artifacts...");
        Map<String, String> idMap = createMultipleArtifacts(10);
        LOGGER.info("Created  {} artifacts", idMap.size());

        deleteMultipleArtifacts(idMap);

        for (Map.Entry entry : idMap.entrySet()) {
            try {
                apicurioService.getLatestArtifact(entry.getValue().toString());
            } catch (WebApplicationException e) {
                assertThat("{\"message\":\"No artifact with ID '" + entry.getValue() + "' was found.\",\"error_code\":404}", is(e.getResponse().readEntity(String.class)));
            }
        }
    }

    @Test
    void deleteArtifactSpecificVersion() {
        ByteArrayInputStream artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecordx\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}".getBytes());
        String artifactId = "deleteArtifactSpecificVersionId";
        ArtifactMetaData metaData = ArtifactUtils.createArtifact(apicurioService, artifactId, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData.toString());

        JsonObject response = new JsonObject(apicurioService.getLatestArtifact(artifactId).readEntity(String.class));

        LOGGER.info("Created record with name:{} and content:{}", response.getString("name"), response.toString());

        for (int x = 0; x < 9; x++) {
            String artifactDefinition = "{\"type\":\"record\",\"name\":\"myrecordx\",\"fields\":[{\"name\":\"foo" + x + "\",\"type\":\"string\"}]}";
            artifactData = new ByteArrayInputStream(artifactDefinition.getBytes());
            metaData = ArtifactUtils.updateArtifact(apicurioService, artifactId, artifactData);
            LOGGER.info("Artifact with ID {} was updated: {}", artifactId, metaData.toString());
        }

        List<Long> artifactVersions = apicurioService.listArtifactVersions(artifactId);

        LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId, artifactVersions.toString());
        assertThat(artifactVersions, hasItems(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L));

        apicurioService.deleteArtifactVersion(4, artifactId);
        LOGGER.info("Version 4 of artifact {} was deleted", artifactId);

        artifactVersions = apicurioService.listArtifactVersions(artifactId);

        LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId, artifactVersions.toString());
        assertThat(artifactVersions, hasItems(1L, 2L, 3L, 5L, 6L, 7L, 8L, 9L, 10L));
        assertThat(artifactVersions, not(hasItems(4L)));

        try {
            apicurioService.getArtifactVersion(4, artifactId);
        } catch (WebApplicationException e) {
            assertThat("{\"message\":\"No version '4' found for artifact with ID 'deleteArtifactSpecificVersionId'.\",\"error_code\":404}", is(e.getResponse().readEntity(String.class)));
        }

        artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecordx\",\"fields\":[{\"name\":\"foo11\",\"type\":\"string\"}]}".getBytes());
        metaData = ArtifactUtils.updateArtifact(apicurioService, artifactId, artifactData);
        LOGGER.info("Artifact with ID {} was updated: {}", artifactId, metaData.toString());

        artifactVersions = apicurioService.listArtifactVersions(artifactId);

        LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId, artifactVersions.toString());

        assertThat(artifactVersions, hasItems(1L, 2L, 3L, 5L, 6L, 7L, 8L, 9L, 10L, 11L));
        assertThat(artifactVersions, not(hasItems(4L)));
    }

    @Test
    void createNonAvroArtifact() {
        ByteArrayInputStream artifactData = new ByteArrayInputStream("{\"type\":\"INVALID\",\"config\":\"invalid\"}".getBytes());
        String artifactId = "artifactWithNonAvroFormatId";

        ArtifactMetaData metaData = ArtifactUtils.createArtifact(apicurioService, artifactId, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData.toString());

        JsonObject response = new JsonObject(apicurioService.getLatestArtifact(artifactId).readEntity(String.class));

        LOGGER.info("Got info about artifact with ID {}: {}", artifactId, response.toString());
        assertThat(response.getString("type"), is("INVALID"));
        assertThat(response.getString("config"), is("invalid"));
    }

    @Test
    void createArtifactSpecificVersion() {
        ByteArrayInputStream artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}".getBytes());
        String artifactId = "createArtifactSpecificVersionId";
        ArtifactMetaData metaData = ArtifactUtils.createArtifact(apicurioService, artifactId, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData.toString());

        artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}".getBytes());
        metaData = ArtifactUtils.updateArtifact(apicurioService, artifactId, artifactData);
        LOGGER.info("Artifact with ID {} was updated: {}", artifactId, metaData.toString());

        List<Long> artifactVersions = apicurioService.listArtifactVersions(artifactId);

        LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId, artifactVersions.toString());
        assertThat(artifactVersions, hasItems(1L, 2L));
    }

    @Test
    void testDuplicatedArtifact() {
        ByteArrayInputStream artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}".getBytes());
        String artifactId = "duplicateArtifactId";
        ArtifactMetaData metaData = ArtifactUtils.createArtifact(apicurioService, artifactId, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData.toString());

        artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"alreadyExistArtifact\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}".getBytes());

        try {
            metaData = ArtifactUtils.createArtifact(apicurioService, artifactId, artifactData);
        } catch (WebApplicationException e) {
            assertThat("{\"message\":\"An artifact with ID 'duplicateArtifactId' already exists.\",\"error_code\":409}", is(e.getResponse().readEntity(String.class)));
        }
    }

    @AfterEach
    void deleteRules() {
        apicurioService.deleteAllGlobalRules();
    }
}

