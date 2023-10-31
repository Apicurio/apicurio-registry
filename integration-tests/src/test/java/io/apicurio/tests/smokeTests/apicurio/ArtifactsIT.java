/*
 * Copyright 2023 Red Hat
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.kiota.ApiException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.utils.AvroGenericRecordSchemaFactory;
import io.apicurio.tests.utils.Constants;
import io.apicurio.registry.rest.client.models.*;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag(Constants.SMOKE)
@QuarkusIntegrationTest
class ArtifactsIT extends ApicurioRegistryBaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtifactsIT.class);

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @Tag(ACCEPTANCE)
    void createAndUpdateArtifact() throws Exception {
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("FULL");

        LOGGER.info("Creating global rule:{}", rule.toString());
        registryClient.admin().rules().post(rule).get(3, TimeUnit.SECONDS);

        // Make sure we have rule
        retryOp((rc) -> rc.admin().rules().byRule(rule.getType().name()).get().get(3, TimeUnit.SECONDS));

        String groupId = TestUtils.generateGroupId();

        String artifactId = TestUtils.generateArtifactId();

        var artifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        ArtifactMetaData amd1 = createArtifact(groupId, artifactId, ArtifactType.AVRO, IoUtil.toStream(artifactData));
        LOGGER.info("Created artifact {} with metadata {}", artifactId, amd1.toString());

        InputStream latest = registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get().get(3, TimeUnit.SECONDS);
        JsonNode response = mapper.readTree(latest);

        LOGGER.info("Artifact with name:{} and content:{} was created", response.get("name").asText(), response);

        String invalidArtifactDefinition = "<type>record</type>\n<name>test</name>";
        String invalidArtifactId = "createAndUpdateArtifactId2";

        LOGGER.info("Invalid artifact sent {}", invalidArtifactDefinition);
        ArtifactContent content = new ArtifactContent();
        content.setContent(invalidArtifactDefinition);

        assertClientError("RuleViolationException", 409, () ->
                registryClient.groups().byGroupId("ccc").artifacts().post(content, config -> {
                    config.headers.add("X-Registry-ArtifactId", invalidArtifactId);
                    config.headers.add("X-Registry-ArtifactType", ArtifactType.AVRO);
                }).get(3, TimeUnit.SECONDS), errorCodeExtractor);

        artifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"long\"}]}";
        content.setContent(artifactData);
        ArtifactMetaData metaData = registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(content).get(3, TimeUnit.SECONDS);
        LOGGER.info("Artifact with ID {} was updated: {}", artifactId, metaData.toString());
        // Make sure artifact is fully registered
        ArtifactMetaData amd2 = metaData;
        retryOp((rc) -> rc.ids().globalIds().byGlobalId(amd2.getGlobalId()).get().get(3, TimeUnit.SECONDS));

        latest = registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get().get(3, TimeUnit.SECONDS);
        response = mapper.readTree(latest);

        LOGGER.info("Artifact with ID {} was updated: {}", artifactId, response);

        List<String> apicurioVersions = listArtifactVersions(registryClient, groupId, artifactId);

        LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId, apicurioVersions.toString());
        assertThat(apicurioVersions, hasItems("1", "2"));

        InputStream version1 = registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion("1").get().get(3, TimeUnit.SECONDS);
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
        registryClient.groups().byGroupId(groupId).delete().get(3, TimeUnit.SECONDS);

        for (ArtifactMetaData artifact : artifacts) {
            retryAssertClientError("ArtifactNotFoundException", 404, (rc) ->
                    rc.groups().byGroupId(artifact.getGroupId()).artifacts().byArtifactId(artifact.getId()).meta().get().get(3, TimeUnit.SECONDS), errorCodeExtractor);
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

        InputStream latest = registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get().get(3, TimeUnit.SECONDS);
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
        assertClientError("ArtifactAlreadyExistsException", 409, () -> createArtifact(groupId, artifactId, ArtifactType.AVRO, iad), true, errorCodeExtractor);
    }

    @Test
    @Tag(ACCEPTANCE)
    void testVersionAlreadyExistsIfExistsUpdate() throws Exception {
        ByteArrayInputStream artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}".getBytes(StandardCharsets.UTF_8));
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        ArtifactMetaData metaData = createArtifact(groupId, artifactId, "1.1", "FAIL", ArtifactType.AVRO, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData.toString());

        ByteArrayInputStream sameArtifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}".getBytes(StandardCharsets.UTF_8));

        assertClientError("VersionAlreadyExistsException", 409, () -> createArtifact(groupId, artifactId, "1.1", "UPDATE", ArtifactType.AVRO, sameArtifactData), true, errorCodeExtractor);
    }

    @Test
    @Tag(ACCEPTANCE)
    void testDisableEnableArtifact() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        String artifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        // Create the artifact
        ArtifactMetaData metaData = createArtifact(groupId, artifactId, ArtifactType.AVRO, IoUtil.toStream(artifactData));
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData.toString());

        // Disable the artifact
        UpdateState data = new UpdateState();
        data.setState(ArtifactState.DISABLED);
        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).state().put(data).get(3, TimeUnit.SECONDS);

        // Verify (expect 404)
        retryOp((rc) -> {
            VersionMetaData actualMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion(metaData.getVersion()).meta().get().get(3, TimeUnit.SECONDS);
            assertEquals(ArtifactState.DISABLED, actualMD.getState());
            assertClientError("ArtifactNotFoundException", 404, () -> rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get().get(3, TimeUnit.SECONDS), errorCodeExtractor);
        });

        // Re-enable the artifact
        data.setState(ArtifactState.ENABLED);
        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).state().put(data).get(3, TimeUnit.SECONDS);

        // Verify
        retryOp((rc) -> {
            ArtifactMetaData actualMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get().get(3, TimeUnit.SECONDS);
            assertEquals(metaData.getGlobalId(), actualMD.getGlobalId());
            assertEquals(ArtifactState.ENABLED, actualMD.getState());
            assertTrue(rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get().get(3, TimeUnit.SECONDS).readAllBytes().length > 0);
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
        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion(String.valueOf(v3MD.getVersion())).state().put(data).get(3, TimeUnit.SECONDS);

        // Verify artifact
        retryOp((rc) -> {
            ArtifactMetaData actualMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get().get(3, TimeUnit.SECONDS);
            assertEquals("2", actualMD.getVersion());

            // Verify v1
            VersionMetaData actualVMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion(String.valueOf(v1MD.getVersion())).meta().get().get(3, TimeUnit.SECONDS);
            assertEquals(ArtifactState.ENABLED, actualVMD.getState());
            // Verify v2
            actualVMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion(String.valueOf(v2MD.getVersion())).meta().get().get(3, TimeUnit.SECONDS);
            assertEquals(ArtifactState.ENABLED, actualVMD.getState());
            // Verify v3
            actualVMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion(String.valueOf(v3MD.getVersion())).meta().get().get(3, TimeUnit.SECONDS);
            assertEquals(ArtifactState.DISABLED, actualVMD.getState());
        });

        // Re-enable v3
        data.setState(ArtifactState.ENABLED);
        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion(String.valueOf(v3MD.getVersion())).state().put(data).get(3, TimeUnit.SECONDS);

        retryOp((rc) -> {
            // Verify artifact (now v3)
            ArtifactMetaData actualMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get().get(3, TimeUnit.SECONDS);
            assertEquals(ArtifactState.ENABLED, actualMD.getState());
            assertEquals("3", actualMD.getVersion()); // version 2 is active (3 is disabled)

            // Verify v1
            VersionMetaData actualVMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion(String.valueOf(v1MD.getVersion())).meta().get().get(3, TimeUnit.SECONDS);
            assertEquals(ArtifactState.ENABLED, actualVMD.getState());
            // Verify v2
            actualVMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion(String.valueOf(v2MD.getVersion())).meta().get().get(3, TimeUnit.SECONDS);
            assertEquals(ArtifactState.ENABLED, actualVMD.getState());
            // Verify v3
            actualVMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion(String.valueOf(v3MD.getVersion())).meta().get().get(3, TimeUnit.SECONDS);
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
            ArtifactMetaData actualMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get().get(3, TimeUnit.SECONDS);
            assertEquals(metaData.getGlobalId(), actualMD.getGlobalId());
            assertEquals(ArtifactState.ENABLED, actualMD.getState());
        });

        // Deprecate the artifact
        UpdateState data = new UpdateState();
        data.setState(ArtifactState.DEPRECATED);
        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).state().put(data).get(3, TimeUnit.SECONDS);

        retryOp((rc) -> {
            // Verify (expect 404)
            ArtifactMetaData actualMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get().get(3, TimeUnit.SECONDS);
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
        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion(String.valueOf(v2MD.getVersion())).state().put(data).get(3, TimeUnit.SECONDS);

        retryOp((rc) -> {
            // Verify artifact
            ArtifactMetaData actualMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get().get(3, TimeUnit.SECONDS);
            assertEquals(ArtifactState.ENABLED, actualMD.getState());

            // Verify v1
            VersionMetaData actualVMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion(String.valueOf(v1MD.getVersion())).meta().get().get(3, TimeUnit.SECONDS);
            assertEquals(ArtifactState.ENABLED, actualVMD.getState());
            // Verify v2
            actualVMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion(String.valueOf(v2MD.getVersion())).meta().get().get(3, TimeUnit.SECONDS);
            assertEquals(ArtifactState.DEPRECATED, actualVMD.getState());
            // Verify v3
            actualVMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion(String.valueOf(v3MD.getVersion())).meta().get().get(3, TimeUnit.SECONDS);
            assertEquals(ArtifactState.ENABLED, actualVMD.getState());
        });
    }

    @Test
    void deleteNonexistingSchema() throws Exception {
        assertClientError("ArtifactNotFoundException", 404, () ->
                registryClient.groups().byGroupId("nonn-existent-group").artifacts().byArtifactId("non-existing").get().get(3, TimeUnit.SECONDS), errorCodeExtractor);
    }

    @Test
    void testForbiddenSpecialCharacters() throws Exception {
//        forbiddenSpecialCharactersTestClient("ab%c");
        forbiddenSpecialCharactersTestClient("._:-ç'`¡¿?0=)(/&$·!ªº<>,;,:");
    }

    void forbiddenSpecialCharactersTestClient(String artifactId) {
        String groupId = TestUtils.generateGroupId();
        String content = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        ArtifactContent artifactData = new ArtifactContent();
        artifactData.setContent(content);

        var executionException = assertThrows(ExecutionException.class, () -> {
            registryClient.groups().byGroupId(groupId).artifacts().post(artifactData, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
                config.headers.add("X-Registry-ArtifactType", ArtifactType.AVRO);
            }).get(3, TimeUnit.SECONDS);});
        // "InvalidArtifactIdException"
        assertNotNull(executionException.getCause());
        // It's impossible to send those characters as early checks are kicking in in Kiota code
        if (artifactId.contains("%")) {
            Assertions.assertEquals(ApiException.class, executionException.getCause().getClass());
            Assertions.assertEquals(404, ((ApiException)executionException.getCause()).getResponseStatusCode());
        } else {
            Assertions.assertEquals(IllegalArgumentException.class, executionException.getCause().getClass());
        }
//        Assertions.assertEquals(io.apicurio.registry.rest.client.models.Error.class, executionException.getCause().getClass());
//        Assertions.assertEquals(400, ((io.apicurio.registry.rest.client.models.Error)executionException.getCause()).getErrorCode());
//        Assertions.assertEquals("InvalidArtifactIdException", ((io.apicurio.registry.rest.client.models.Error)executionException.getCause()).getName());

//        createArtifact(groupId, artifactId, content, 400);
    }

    @Test
    void testAllowedSpecialCharacters() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "._:-'`?0=)(/&$!<>,;,:";

        String content = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        ArtifactContent artifactContent = new ArtifactContent();
        artifactContent.setContent(content);
        createArtifact(groupId, artifactId, ArtifactType.AVRO, IoUtil.toStream(content));

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get().get(3, TimeUnit.SECONDS);

        retryOp((rc) -> rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().post(artifactContent).get(3, TimeUnit.SECONDS));

        registryClient.search().artifacts().get(config -> {
            config.queryParameters.group = groupId;
            config.queryParameters.name = artifactId;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
        }).get(3, TimeUnit.SECONDS);

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).test().put(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))).get(3, TimeUnit.SECONDS);

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(artifactContent).get(3, TimeUnit.SECONDS);

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get().get(3, TimeUnit.SECONDS);

        getArtifact(groupId, artifactId);
    }

    @Test
    void testAllowedSpecialCharactersCreateViaApi() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "._:-'`?0=)(/&$!<>,;,:";

        String content = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        ArtifactContent artifactContent = new ArtifactContent();
        artifactContent.setContent(content);

        createArtifact(groupId, artifactId, content, 200);

        retryOp((rc) -> rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get().get(3, TimeUnit.SECONDS));

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get().get(3, TimeUnit.SECONDS);

        retryOp((rc) -> rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().post(artifactContent).get(3, TimeUnit.SECONDS));

        registryClient.search().artifacts().get(config -> {
            config.queryParameters.group = groupId;
            config.queryParameters.name = artifactId;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 100;
        }).get(3, TimeUnit.SECONDS);

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).test().put(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))).get(3, TimeUnit.SECONDS);

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(artifactContent).get(3, TimeUnit.SECONDS);

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();

        getArtifact(groupId, artifactId);
    }

    @Test
    @Tag(ACCEPTANCE)
    public void testSearchOrderBy() throws Exception {
        String group = UUID.randomUUID().toString();
        String content = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        for (int idx = 0; idx < 5; idx++) {
            String artifactId = "test-" + idx;
            Thread.sleep(idx == 0 ? 0 : 1500 / idx);
            this.createArtifact(group, artifactId, ArtifactType.OPENAPI, new ByteArrayInputStream(content.getBytes()));
        }

        ArtifactSearchResults results = registryClient.search().artifacts().get(config -> {
            config.queryParameters.group = group;
            config.queryParameters.order = "asc";
            config.queryParameters.orderby = "createdOn";
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
        }).get(3, TimeUnit.SECONDS);

        Assertions.assertNotNull(results);
        Assertions.assertEquals(5, results.getCount());
        Assertions.assertEquals(5, results.getArtifacts().size());
        Assertions.assertEquals("test-0", results.getArtifacts().get(0).getId());

    }

    @AfterEach
    void deleteRules() throws Exception {
        try {
            registryClient.admin().rules().delete().get(3, TimeUnit.SECONDS);
            retryOp((rc) -> {
                List<RuleType> rules = rc.admin().rules().get().get(3, TimeUnit.SECONDS);
                assertEquals(0, rules.size(), "All global rules not deleted");
            });
        } catch (Exception e) {
            // ignore
        }
    }
}

