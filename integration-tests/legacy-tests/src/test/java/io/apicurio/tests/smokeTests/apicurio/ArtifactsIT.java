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

import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.rest.beans.UpdateState;
import io.apicurio.registry.rest.beans.VersionMetaData;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.BaseIT;
import io.apicurio.tests.utils.subUtils.ArtifactUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import static io.apicurio.tests.common.Constants.SMOKE;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag(SMOKE)
class ArtifactsIT extends BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtifactsIT.class);

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @Tag(ACCEPTANCE)
    void createAndUpdateArtifact() throws Exception {
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("FULL");

        LOGGER.info("Creating global rule:{}", rule.toString());
        registryClient.createGlobalRule(rule);

        // Make sure we have rule
        TestUtils.retry(() -> registryClient.getGlobalRuleConfig(rule.getType()));

        String artifactId = TestUtils.generateArtifactId();

        ByteArrayInputStream artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}".getBytes(StandardCharsets.UTF_8));
        ArtifactMetaData metaData = ArtifactUtils.createArtifact(registryClient, ArtifactType.AVRO, artifactId, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData.toString());
        // Make sure artifact is fully registered
        ArtifactMetaData amd1 = metaData;
        TestUtils.retry(() -> registryClient.getArtifactMetaDataByGlobalId(amd1.getGlobalId()));

        InputStream latest = registryClient.getLatestArtifact(artifactId);
        JsonNode response = mapper.readTree(latest);

        LOGGER.info("Artifact with name:{} and content:{} was created", response.get("name").asText(), response);

        String invalidArtifactDefinition = "<type>record</type>\n<name>test</name>";
        artifactData = new ByteArrayInputStream(invalidArtifactDefinition.getBytes(StandardCharsets.UTF_8));
        String invalidArtifactId = "createAndUpdateArtifactId2";

        LOGGER.info("Invalid artifact sent {}", invalidArtifactDefinition);
        ByteArrayInputStream iad = artifactData;
        assertWebError(409, () -> ArtifactUtils.createArtifact(registryClient, ArtifactType.AVRO, invalidArtifactId, iad));

        artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"long\"}]}".getBytes(StandardCharsets.UTF_8));
        metaData = ArtifactUtils.updateArtifact(registryClient, ArtifactType.AVRO, artifactId, artifactData);
        LOGGER.info("Artifact with ID {} was updated: {}", artifactId, metaData.toString());
        // Make sure artifact is fully registered
        ArtifactMetaData amd2 = metaData;
        TestUtils.retry(() -> registryClient.getArtifactMetaDataByGlobalId(amd2.getGlobalId()));

        latest = registryClient.getLatestArtifact(artifactId);
        response = mapper.readTree(latest);

        LOGGER.info("Artifact with ID {} was updated: {}", artifactId, response);

        List<Long> apicurioVersions = registryClient.listArtifactVersions(artifactId);

        LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId, apicurioVersions.toString());
        assertThat(apicurioVersions, hasItems(1L, 2L));

        InputStream version1 = registryClient.getArtifactVersion(artifactId, 1);
        response = mapper.readTree(version1);

        LOGGER.info("Artifact with ID {} and version {}: {}", artifactId, 1, response);

        assertThat(response.get("fields").elements().next().get("name").asText(), is("foo"));
    }

    @Test
    void createAndDeleteMultipleArtifacts() throws Exception {
        LOGGER.info("Creating some artifacts...");
        Map<String, String> idMap = createMultipleArtifacts(registryClient, 10);
        LOGGER.info("Created  {} artifacts", idMap.size());

        deleteMultipleArtifacts(registryClient, idMap);

        for (Map.Entry<String, String> entry : idMap.entrySet()) {
            assertWebError(404, () -> registryClient.getLatestArtifact(entry.getValue()), true);
        }
    }

    @Test
    @Tag(ACCEPTANCE)
    void createNonAvroArtifact() throws Exception {
        ByteArrayInputStream artifactData = new ByteArrayInputStream("{\"type\":\"INVALID\",\"config\":\"invalid\"}".getBytes(StandardCharsets.UTF_8));
        String artifactId = TestUtils.generateArtifactId();

        ArtifactMetaData amd = registryClient.createArtifact(artifactId, ArtifactType.JSON, null, artifactData);
        // Make sure artifact is fully registered
        TestUtils.retry(() -> registryClient.getArtifactMetaDataByGlobalId(amd.getGlobalId()));

        LOGGER.info("Created artifact {} with metadata {}", artifactId, amd);

        InputStream latest = registryClient.getLatestArtifact(artifactId);
        JsonNode response = mapper.readTree(latest);

        LOGGER.info("Got info about artifact with ID {}: {}", artifactId, response);
        assertThat(response.get("type").asText(), is("INVALID"));
        assertThat(response.get("config").asText(), is("invalid"));
    }

    @Test
    void createArtifactSpecificVersion() throws Exception {
        ByteArrayInputStream artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}".getBytes(StandardCharsets.UTF_8));
        String artifactId = TestUtils.generateArtifactId();
        ArtifactMetaData metaData = ArtifactUtils.createArtifact(registryClient, ArtifactType.AVRO, artifactId, artifactData);
        // Make sure artifact is fully registered
        ArtifactMetaData amd1 = metaData;
        TestUtils.retry(() -> registryClient.getArtifactMetaDataByGlobalId(amd1.getGlobalId()));
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData);

        artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}".getBytes(StandardCharsets.UTF_8));
        metaData = ArtifactUtils.updateArtifact(registryClient, ArtifactType.AVRO, artifactId, artifactData);
        // Make sure artifact is fully updated
        ArtifactMetaData amd2 = metaData;
        TestUtils.retry(() -> registryClient.getArtifactMetaDataByGlobalId(amd2.getGlobalId()));
        LOGGER.info("Artifact with ID {} was updated: {}", artifactId, metaData);

        List<Long> artifactVersions = registryClient.listArtifactVersions(artifactId);

        LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId, artifactVersions.toString());
        assertThat(artifactVersions, hasItems(1L, 2L));
    }

    @Test
    @Tag(ACCEPTANCE)
    void testDuplicatedArtifact() throws Exception {
        ByteArrayInputStream artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}".getBytes(StandardCharsets.UTF_8));
        String artifactId = TestUtils.generateArtifactId();
        ArtifactMetaData metaData = ArtifactUtils.createArtifact(registryClient, ArtifactType.AVRO, artifactId, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData.toString());

        ByteArrayInputStream iad = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"alreadyExistArtifact\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}".getBytes(StandardCharsets.UTF_8));
        assertWebError(409, () -> ArtifactUtils.createArtifact(registryClient, ArtifactType.AVRO, artifactId, iad), true);
    }

    @Test
    @Tag(ACCEPTANCE)
    void testDisableEnableArtifact() throws Exception {
        String artifactId = TestUtils.generateArtifactId();
        String artifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        // Create the artifact
        ArtifactMetaData metaData = ArtifactUtils.createArtifact(registryClient, ArtifactType.AVRO, artifactId, IoUtil.toStream(artifactData));
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData.toString());

        // Verify
        TestUtils.retry(() -> {
            ArtifactMetaData actualMD = registryClient.getArtifactMetaData(artifactId);
            assertEquals(metaData.getGlobalId(), actualMD.getGlobalId());
        });

        // Disable the artifact
        UpdateState data = new UpdateState();
        data.setState(ArtifactState.DISABLED);
        registryClient.updateArtifactState(artifactId, data);

        // Verify (expect 404)
        TestUtils.retry(() -> {
            ArtifactMetaData actualMD = registryClient.getArtifactMetaData(artifactId);
            assertEquals(ArtifactState.DISABLED, actualMD.getState());
            assertWebError(404, () -> registryClient.getLatestArtifact(artifactId), true);
        });

        // Re-enable the artifact
        data.setState(ArtifactState.ENABLED);
        registryClient.updateArtifactState(artifactId, data);

        // Verify
        TestUtils.retry(() -> {
            ArtifactMetaData actualMD = registryClient.getArtifactMetaData(artifactId);
            assertEquals(metaData.getGlobalId(), actualMD.getGlobalId());
        });
    }

    @Test
    void testDisableEnableArtifactVersion() throws Exception {
        String artifactId = TestUtils.generateArtifactId();
        String artifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        String artifactDataV2 = "{\"type\":\"record\",\"name\":\"myrecord2\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        String artifactDataV3 = "{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        // Create the artifact
        ArtifactMetaData v1MD = ArtifactUtils.createArtifact(registryClient, ArtifactType.AVRO, artifactId, IoUtil.toStream(artifactData));
        LOGGER.info("Created artifact {} with metadata {}", artifactId, v1MD.toString());
        TestUtils.retry(() -> registryClient.getArtifactMetaDataByGlobalId(v1MD.getGlobalId()));

        // Update the artifact (v2)
        ArtifactMetaData v2MD = ArtifactUtils.updateArtifact(registryClient, ArtifactType.AVRO, artifactId, IoUtil.toStream(artifactDataV2));
        TestUtils.retry(() -> registryClient.getArtifactMetaDataByGlobalId(v2MD.getGlobalId()));

        // Update the artifact (v3)
        ArtifactMetaData v3MD = ArtifactUtils.updateArtifact(registryClient, ArtifactType.AVRO, artifactId, IoUtil.toStream(artifactDataV3));
        TestUtils.retry(() -> registryClient.getArtifactMetaDataByGlobalId(v3MD.getGlobalId()));

        // Disable v3
        UpdateState data = new UpdateState();
        data.setState(ArtifactState.DISABLED);
        registryClient.updateArtifactVersionState(artifactId, v3MD.getVersion(), data);

        // Verify artifact
        TestUtils.retry(() -> {
            ArtifactMetaData actualMD = registryClient.getArtifactMetaData(artifactId);
            assertEquals(ArtifactState.DISABLED, actualMD.getState());
            assertEquals(3, actualMD.getVersion());

            // Verify v1
            VersionMetaData actualVMD = registryClient.getArtifactVersionMetaData(artifactId, v1MD.getVersion());
            assertEquals(ArtifactState.ENABLED, actualVMD.getState());
            // Verify v2
            actualVMD = registryClient.getArtifactVersionMetaData(artifactId, v2MD.getVersion());
            assertEquals(ArtifactState.ENABLED, actualVMD.getState());
            // Verify v3
            actualVMD = registryClient.getArtifactVersionMetaData(artifactId, v3MD.getVersion());
            assertEquals(ArtifactState.DISABLED, actualVMD.getState());
        });

        // Re-enable v3
        data.setState(ArtifactState.ENABLED);
        registryClient.updateArtifactVersionState(artifactId, v3MD.getVersion(), data);

        TestUtils.retry(() -> {
            // Verify artifact (now v3)
            ArtifactMetaData actualMD = registryClient.getArtifactMetaData(artifactId);
            assertEquals(ArtifactState.ENABLED, actualMD.getState());
            assertEquals(3, actualMD.getVersion()); // version 2 is active (3 is disabled)

            // Verify v1
            VersionMetaData actualVMD = registryClient.getArtifactVersionMetaData(artifactId, v1MD.getVersion());
            assertEquals(ArtifactState.ENABLED, actualVMD.getState());
            // Verify v2
            actualVMD = registryClient.getArtifactVersionMetaData(artifactId, v2MD.getVersion());
            assertEquals(ArtifactState.ENABLED, actualVMD.getState());
            // Verify v3
            actualVMD = registryClient.getArtifactVersionMetaData(artifactId, v3MD.getVersion());
            assertEquals(ArtifactState.ENABLED, actualVMD.getState());
        });
    }

    @Test
    void testDeprecateArtifact() throws Exception {
        String artifactId = TestUtils.generateArtifactId();
        String artifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        // Create the artifact
        ArtifactMetaData metaData = ArtifactUtils.createArtifact(registryClient, ArtifactType.AVRO, artifactId, IoUtil.toStream(artifactData));
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData.toString());

        TestUtils.retry(() -> {
            // Verify
            ArtifactMetaData actualMD = registryClient.getArtifactMetaData(artifactId);
            assertEquals(metaData.getGlobalId(), actualMD.getGlobalId());
            assertEquals(ArtifactState.ENABLED, actualMD.getState());
        });

        // Deprecate the artifact
        UpdateState data = new UpdateState();
        data.setState(ArtifactState.DEPRECATED);
        registryClient.updateArtifactState(artifactId, data);

        TestUtils.retry(() -> {
            // Verify (expect 404)
            ArtifactMetaData actualMD = registryClient.getArtifactMetaData(artifactId);
            assertEquals(metaData.getGlobalId(), actualMD.getGlobalId());
            assertEquals(ArtifactState.DEPRECATED, actualMD.getState());
        });
    }

    @Test
    void testDeprecateArtifactVersion() throws Exception {
        String artifactId = TestUtils.generateArtifactId();
        String artifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        String artifactDataV2 = "{\"type\":\"record\",\"name\":\"myrecord2\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        String artifactDataV3 = "{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        // Create the artifact
        ArtifactMetaData v1MD = ArtifactUtils.createArtifact(registryClient, ArtifactType.AVRO, artifactId, IoUtil.toStream(artifactData));
        LOGGER.info("Created artifact {} with metadata {}", artifactId, v1MD.toString());
        TestUtils.retry(() -> registryClient.getArtifactMetaDataByGlobalId(v1MD.getGlobalId()));

        // Update the artifact (v2)
        ArtifactMetaData v2MD = ArtifactUtils.updateArtifact(registryClient, ArtifactType.AVRO, artifactId, IoUtil.toStream(artifactDataV2));
        TestUtils.retry(() -> registryClient.getArtifactMetaDataByGlobalId(v2MD.getGlobalId()));

        // Update the artifact (v3)
        ArtifactMetaData v3MD = ArtifactUtils.updateArtifact(registryClient, ArtifactType.AVRO, artifactId, IoUtil.toStream(artifactDataV3));
        TestUtils.retry(() -> registryClient.getArtifactMetaDataByGlobalId(v3MD.getGlobalId()));

        // Deprecate v2
        UpdateState data = new UpdateState();
        data.setState(ArtifactState.DEPRECATED);
        registryClient.updateArtifactVersionState(artifactId, v2MD.getVersion(), data);

        TestUtils.retry(() -> {
            // Verify artifact
            ArtifactMetaData actualMD = registryClient.getArtifactMetaData(artifactId);
            assertEquals(ArtifactState.ENABLED, actualMD.getState());

            // Verify v1
            VersionMetaData actualVMD = registryClient.getArtifactVersionMetaData(artifactId, v1MD.getVersion());
            assertEquals(ArtifactState.ENABLED, actualVMD.getState());
            // Verify v2
            actualVMD = registryClient.getArtifactVersionMetaData(artifactId, v2MD.getVersion());
            assertEquals(ArtifactState.DEPRECATED, actualVMD.getState());
            // Verify v3
            actualVMD = registryClient.getArtifactVersionMetaData(artifactId, v3MD.getVersion());
            assertEquals(ArtifactState.ENABLED, actualVMD.getState());
        });
    }

    @Test
    void deleteNonexistingSchema() throws Exception {
        assertWebError(404, () -> registryClient.deleteArtifact("non-existing"));
    }

    @AfterEach
    void deleteRules() throws Exception {
        registryClient.deleteAllGlobalRules();
        TestUtils.retry(() -> {
            List<RuleType> rules = registryClient.listGlobalRules();
            assertEquals(0, rules.size(), "All global rules not deleted");
        });
    }
}

