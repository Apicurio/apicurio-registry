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

import io.apicurio.registry.rest.client.exception.ArtifactAlreadyExistsException;
import io.apicurio.registry.rest.client.exception.ArtifactNotFoundException;
import io.apicurio.registry.rest.client.exception.InvalidArtifactIdException;
import io.apicurio.registry.rest.client.exception.RuleViolationException;
import io.apicurio.registry.rest.client.exception.VersionAlreadyExistsException;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rest.v2.beans.SortBy;
import io.apicurio.registry.rest.v2.beans.SortOrder;
import io.apicurio.registry.rest.v2.beans.UpdateState;
import io.apicurio.registry.rest.v2.beans.VersionMetaData;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioV2BaseIT;
import io.apicurio.tests.common.Constants;
import io.apicurio.tests.serdes.apicurio.AvroGenericRecordSchemaFactory;
import io.apicurio.tests.utils.ArtifactUtils;

import static io.apicurio.registry.utils.tests.TestUtils.assertClientError;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Tag(Constants.SMOKE)
class ArtifactsIT extends ApicurioV2BaseIT {

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
        retryOp((rc) -> rc.getGlobalRuleConfig(rule.getType()));

        String groupId = TestUtils.generateArtifactId();

        String artifactId = TestUtils.generateArtifactId();

        ByteArrayInputStream artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}".getBytes(StandardCharsets.UTF_8));

        ArtifactMetaData amd1 = createArtifact(groupId, artifactId, ArtifactType.AVRO, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, amd1.toString());

        InputStream latest = registryClient.getLatestArtifact(groupId, artifactId);
        JsonNode response = mapper.readTree(latest);

        LOGGER.info("Artifact with name:{} and content:{} was created", response.get("name").asText(), response);

        String invalidArtifactDefinition = "<type>record</type>\n<name>test</name>";
        artifactData = new ByteArrayInputStream(invalidArtifactDefinition.getBytes(StandardCharsets.UTF_8));
        String invalidArtifactId = "createAndUpdateArtifactId2";

        LOGGER.info("Invalid artifact sent {}", invalidArtifactDefinition);
        ByteArrayInputStream iad = artifactData;
        assertClientError(RuleViolationException.class.getSimpleName(), 409, () -> registryClient.createArtifact(groupId, invalidArtifactId, ArtifactType.AVRO, iad), errorCodeExtractor);

        artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"long\"}]}".getBytes(StandardCharsets.UTF_8));
        ArtifactMetaData metaData = registryClient.updateArtifact(groupId, artifactId, artifactData);
        LOGGER.info("Artifact with ID {} was updated: {}", artifactId, metaData.toString());
        // Make sure artifact is fully registered
        ArtifactMetaData amd2 = metaData;
        retryOp((rc) -> rc.getContentByGlobalId(amd2.getGlobalId()));

        latest = registryClient.getLatestArtifact(groupId, artifactId);
        response = mapper.readTree(latest);

        LOGGER.info("Artifact with ID {} was updated: {}", artifactId, response);

        List<String> apicurioVersions = listArtifactVersions(registryClient, groupId, artifactId);

        LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId, apicurioVersions.toString());
        assertThat(apicurioVersions, hasItems("1", "2"));

        InputStream version1 = registryClient.getArtifactVersion(groupId, artifactId, "1");
        response = mapper.readTree(version1);

        LOGGER.info("Artifact with ID {} and version {}: {}", artifactId, 1, response);

        assertThat(response.get("fields").elements().next().get("name").asText(), is("foo"));
    }

    @Test
    void createAndDeleteMultipleArtifacts() throws Exception {
        LOGGER.info("Creating some artifacts...");
        String groupId = TestUtils.generateGroupId();

        List<ArtifactMetaData> artifacts = IntStream.range(0, 10)
            .mapToObj(i -> {
                String artifactId = TestUtils.generateSubject();
                try {
                    return super.createArtifact(groupId, artifactId, ArtifactType.AVRO, new AvroGenericRecordSchemaFactory(groupId, artifactId, List.of("foo")).generateSchemaStream());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .collect(Collectors.toList());

        LOGGER.info("Created  {} artifacts", artifacts.size());

        LOGGER.info("Removing all artifacts in group {}", groupId);
        registryClient.deleteArtifactsInGroup(groupId);

        for (ArtifactMetaData artifact : artifacts) {
            retryAssertClientError(ArtifactNotFoundException.class.getSimpleName(), 404, (rc) -> rc.getArtifactMetaData(artifact.getGroupId(), artifact.getId()), errorCodeExtractor);
        }
    }

    @Test
    @Tag(ACCEPTANCE)
    void createNonAvroArtifact() throws Exception {
        String groupId = TestUtils.generateArtifactId();
        ByteArrayInputStream artifactData = new ByteArrayInputStream("{\"type\":\"INVALID\",\"config\":\"invalid\"}".getBytes(StandardCharsets.UTF_8));
        String artifactId = TestUtils.generateArtifactId();

        ArtifactMetaData amd = createArtifact(groupId, artifactId, ArtifactType.JSON, artifactData);

        LOGGER.info("Created artifact {} with metadata {}", artifactId, amd);

        InputStream latest = registryClient.getLatestArtifact(groupId, artifactId);
        JsonNode response = mapper.readTree(latest);

        LOGGER.info("Got info about artifact with ID {}: {}", artifactId, response);
        assertThat(response.get("type").asText(), is("INVALID"));
        assertThat(response.get("config").asText(), is("invalid"));
    }

    @Test
    void createArtifactSpecificVersion() throws Exception {
        String groupId = TestUtils.generateGroupId();
        ByteArrayInputStream artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}".getBytes(StandardCharsets.UTF_8));
        String artifactId = TestUtils.generateArtifactId();
        ArtifactMetaData metaData = createArtifact(groupId, artifactId, ArtifactType.AVRO, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData);

        artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}".getBytes(StandardCharsets.UTF_8));
        metaData = updateArtifact(groupId, artifactId, artifactData);
        LOGGER.info("Artifact with ID {} was updated: {}", artifactId, metaData);

        List<String> artifactVersions = listArtifactVersions(registryClient, groupId, artifactId);

        LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId, artifactVersions.toString());
        assertThat(artifactVersions, hasItems("1", "2"));
    }

    @Test
    @Tag(ACCEPTANCE)
    void testDuplicatedArtifact() throws Exception {
        ByteArrayInputStream artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}".getBytes(StandardCharsets.UTF_8));
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        ArtifactMetaData metaData = createArtifact(groupId, artifactId, ArtifactType.AVRO, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData.toString());

        ByteArrayInputStream iad = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"alreadyExistArtifact\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}".getBytes(StandardCharsets.UTF_8));
        assertClientError(ArtifactAlreadyExistsException.class.getSimpleName(), 409, () -> createArtifact(groupId, artifactId, ArtifactType.AVRO, iad), true, errorCodeExtractor);
    }

    @Test
    @Tag(ACCEPTANCE)
    void testVersionAlreadyExistsIfExistsUpdate() throws Exception {
        ByteArrayInputStream artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}".getBytes(StandardCharsets.UTF_8));
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        ArtifactMetaData metaData = createArtifact(groupId, artifactId, "1.1", IfExists.FAIL, ArtifactType.AVRO, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData.toString());

        ByteArrayInputStream sameArtifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}".getBytes(StandardCharsets.UTF_8));

        assertClientError(VersionAlreadyExistsException.class.getSimpleName(), 409, () -> createArtifact(groupId, artifactId, "1.1", IfExists.UPDATE, ArtifactType.AVRO, sameArtifactData), true, errorCodeExtractor);
    }

    @Test
    @Tag(ACCEPTANCE)
    void testDisableEnableArtifact() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        String artifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        // Create the artifact
        ArtifactMetaData metaData = createArtifact(groupId, artifactId , ArtifactType.AVRO, IoUtil.toStream(artifactData));
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData.toString());

        // Disable the artifact
        UpdateState data = new UpdateState();
        data.setState(ArtifactState.DISABLED);
        registryClient.updateArtifactState(groupId, artifactId, data);

        // Verify (expect 404)
        retryOp((rc) -> {
            ArtifactMetaData actualMD = rc.getArtifactMetaData(groupId, artifactId);
            assertEquals(ArtifactState.DISABLED, actualMD.getState());
            assertClientError(ArtifactNotFoundException.class.getSimpleName(), 404, () -> rc.getLatestArtifact(groupId, artifactId), errorCodeExtractor);
        });

        // Re-enable the artifact
        data.setState(ArtifactState.ENABLED);
        registryClient.updateArtifactState(groupId, artifactId, data);

        // Verify
        retryOp((rc) -> {
            ArtifactMetaData actualMD = rc.getArtifactMetaData(groupId, artifactId);
            assertEquals(metaData.getGlobalId(), actualMD.getGlobalId());
            assertEquals(ArtifactState.ENABLED, actualMD.getState());
            assertNotNull(registryClient.getLatestArtifact(groupId, artifactId));
        });
    }

    @Test
    void testDisableEnableArtifactVersion() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        String artifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        String artifactDataV2 = "{\"type\":\"record\",\"name\":\"myrecord2\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        String artifactDataV3 = "{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        // Create the artifact
        ArtifactMetaData v1MD = createArtifact(groupId, artifactId, ArtifactType.AVRO, IoUtil.toStream(artifactData));
        LOGGER.info("Created artifact {} with metadata {}", artifactId, v1MD.toString());

        // Update the artifact (v2)
        ArtifactMetaData v2MD = updateArtifact(groupId, artifactId, IoUtil.toStream(artifactDataV2));

        // Update the artifact (v3)
        ArtifactMetaData v3MD = updateArtifact(groupId, artifactId, IoUtil.toStream(artifactDataV3));

        // Disable v3
        UpdateState data = new UpdateState();
        data.setState(ArtifactState.DISABLED);
        registryClient.updateArtifactVersionState(groupId, artifactId, String.valueOf(v3MD.getVersion()), data);

        // Verify artifact
        retryOp((rc) -> {
            ArtifactMetaData actualMD = rc.getArtifactMetaData(groupId, artifactId);
            assertEquals(ArtifactState.DISABLED, actualMD.getState());
            assertEquals("3", actualMD.getVersion());

            // Verify v1
            VersionMetaData actualVMD = rc.getArtifactVersionMetaData(groupId, artifactId, String.valueOf(v1MD.getVersion()));
            assertEquals(ArtifactState.ENABLED, actualVMD.getState());
            // Verify v2
            actualVMD = rc.getArtifactVersionMetaData(groupId, artifactId, String.valueOf(v2MD.getVersion()));
            assertEquals(ArtifactState.ENABLED, actualVMD.getState());
            // Verify v3
            actualVMD = rc.getArtifactVersionMetaData(groupId, artifactId, String.valueOf(v3MD.getVersion()));
            assertEquals(ArtifactState.DISABLED, actualVMD.getState());
        });

        // Re-enable v3
        data.setState(ArtifactState.ENABLED);
        registryClient.updateArtifactVersionState(groupId, artifactId, String.valueOf(v3MD.getVersion()), data);

        retryOp((rc) -> {
            // Verify artifact (now v3)
            ArtifactMetaData actualMD = rc.getArtifactMetaData(groupId, artifactId);
            assertEquals(ArtifactState.ENABLED, actualMD.getState());
            assertEquals("3", actualMD.getVersion()); // version 2 is active (3 is disabled)

            // Verify v1
            VersionMetaData actualVMD = rc.getArtifactVersionMetaData(groupId, artifactId, String.valueOf(v1MD.getVersion()));
            assertEquals(ArtifactState.ENABLED, actualVMD.getState());
            // Verify v2
            actualVMD = rc.getArtifactVersionMetaData(groupId, artifactId, String.valueOf(v2MD.getVersion()));
            assertEquals(ArtifactState.ENABLED, actualVMD.getState());
            // Verify v3
            actualVMD = rc.getArtifactVersionMetaData(groupId, artifactId, String.valueOf(v3MD.getVersion()));
            assertEquals(ArtifactState.ENABLED, actualVMD.getState());
        });
    }

    @Test
    void testDeprecateArtifact() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        String artifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        // Create the artifact
        ArtifactMetaData metaData = createArtifact(groupId, artifactId, ArtifactType.AVRO, IoUtil.toStream(artifactData));
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData.toString());

        retryOp((rc) -> {
            // Verify
            ArtifactMetaData actualMD = rc.getArtifactMetaData(groupId, artifactId);
            assertEquals(metaData.getGlobalId(), actualMD.getGlobalId());
            assertEquals(ArtifactState.ENABLED, actualMD.getState());
        });

        // Deprecate the artifact
        UpdateState data = new UpdateState();
        data.setState(ArtifactState.DEPRECATED);
        registryClient.updateArtifactState(groupId, artifactId, data);

        retryOp((rc) -> {
            // Verify (expect 404)
            ArtifactMetaData actualMD = rc.getArtifactMetaData(groupId, artifactId);
            assertEquals(metaData.getGlobalId(), actualMD.getGlobalId());
            assertEquals(ArtifactState.DEPRECATED, actualMD.getState());
        });
    }

    @Test
    void testDeprecateArtifactVersion() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        String artifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        String artifactDataV2 = "{\"type\":\"record\",\"name\":\"myrecord2\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        String artifactDataV3 = "{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        // Create the artifact
        ArtifactMetaData v1MD = createArtifact(groupId, artifactId, ArtifactType.AVRO, IoUtil.toStream(artifactData));
        LOGGER.info("Created artifact {} with metadata {}", artifactId, v1MD.toString());

        // Update the artifact (v2)
        ArtifactMetaData v2MD = updateArtifact(groupId, artifactId, IoUtil.toStream(artifactDataV2));

        // Update the artifact (v3)
        ArtifactMetaData v3MD = updateArtifact(groupId, artifactId, IoUtil.toStream(artifactDataV3));

        // Deprecate v2
        UpdateState data = new UpdateState();
        data.setState(ArtifactState.DEPRECATED);
        registryClient.updateArtifactVersionState(groupId, artifactId, String.valueOf(v2MD.getVersion()), data);

        retryOp((rc) -> {
            // Verify artifact
            ArtifactMetaData actualMD = rc.getArtifactMetaData(groupId, artifactId);
            assertEquals(ArtifactState.ENABLED, actualMD.getState());

            // Verify v1
            VersionMetaData actualVMD = rc.getArtifactVersionMetaData(groupId, artifactId, String.valueOf(v1MD.getVersion()));
            assertEquals(ArtifactState.ENABLED, actualVMD.getState());
            // Verify v2
            actualVMD = rc.getArtifactVersionMetaData(groupId, artifactId, String.valueOf(v2MD.getVersion()));
            assertEquals(ArtifactState.DEPRECATED, actualVMD.getState());
            // Verify v3
            actualVMD = rc.getArtifactVersionMetaData(groupId, artifactId, String.valueOf(v3MD.getVersion()));
            assertEquals(ArtifactState.ENABLED, actualVMD.getState());
        });
    }

    @Test
    void deleteNonexistingSchema() throws Exception {
        assertClientError(ArtifactNotFoundException.class.getSimpleName(), 404, () -> registryClient.deleteArtifact("nonn-existent-group", "non-existing"), errorCodeExtractor);
    }

    @Test
    void testForbiddenSpecialCharacters() throws Exception {
        forbiddenSpecialCharactersTestClient("ab%c");
        forbiddenSpecialCharactersTestClient("._:-ç'`¡¿?0=)(/&$·!ªº<>,;,:");
    }

    void forbiddenSpecialCharactersTestClient(String artifactId) {
        String groupId = TestUtils.generateGroupId();
        String content = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        ByteArrayInputStream artifactData = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        Assertions.assertThrows(InvalidArtifactIdException.class, () -> {
            registryClient.createArtifact(groupId, artifactId, ArtifactType.AVRO, artifactData);
        });

        ArtifactUtils.createArtifact(groupId, artifactId, content, 400);

    }

    @Test
    void testAllowedSpecialCharacters() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "._:-'`?0=)(/&$!<>,;,:";

        String content = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        ByteArrayInputStream artifactData = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        createArtifact(groupId, artifactId, ArtifactType.AVRO, artifactData);

        registryClient.getArtifactMetaData(groupId, artifactId);

        retryOp((rc) -> rc.getArtifactVersionMetaDataByContent(groupId, artifactId, false, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));

        registryClient.listArtifactVersions(groupId, artifactId, 0, 10);

        registryClient.testUpdateArtifact(groupId, artifactId, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        registryClient.updateArtifact(groupId, artifactId, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        registryClient.getLatestArtifact(groupId, artifactId);

        ArtifactUtils.getArtifact(groupId, artifactId);
    }

    @Test
    void testAllowedSpecialCharactersCreateViaApi() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "._:-'`?0=)(/&$!<>,;,:";

        String content = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        ArtifactUtils.createArtifact(groupId, artifactId, content, 200);

        retryOp((rc) -> rc.getArtifactMetaData(groupId,artifactId));

        registryClient.getArtifactMetaData(groupId, artifactId);

        retryOp((rc) -> rc.getArtifactVersionMetaDataByContent(groupId, artifactId, false, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));

        registryClient.listArtifactVersions(groupId, artifactId, 0, 100);

        registryClient.testUpdateArtifact(groupId, artifactId, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        registryClient.updateArtifact(groupId, artifactId, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        registryClient.getLatestArtifact(groupId, artifactId);

        ArtifactUtils.getArtifact(groupId, artifactId);
    }

    @Test
    @Tag(ACCEPTANCE)
    public void testSearchOrderBy() throws Exception {
        String group = UUID.randomUUID().toString();
        String content = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        for (int idx = 0; idx < 5; idx++) {
            String artifactId = "test-" + idx;
            Thread.sleep(idx == 0 ? 0 : 1500/idx);
            this.createArtifact(group, artifactId, ArtifactType.OPENAPI, new ByteArrayInputStream(content.getBytes()));
        }

        ArtifactSearchResults results = registryClient.searchArtifacts(group, null, null, null, null, SortBy.createdOn, SortOrder.asc, 0, 10);

        Assertions.assertNotNull(results);
        Assertions.assertEquals(5, results.getCount());
        Assertions.assertEquals(5, results.getArtifacts().size());
        Assertions.assertEquals("test-0", results.getArtifacts().get(0).getId());

    }

    @AfterEach
    void deleteRules() throws Exception {
        registryClient.deleteAllGlobalRules();
        retryOp((rc) -> {
            List<RuleType> rules = rc.listGlobalRules();
            assertEquals(0, rules.size(), "All global rules not deleted");
        });
    }
}

