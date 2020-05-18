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

import static io.apicurio.tests.Constants.SMOKE;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.rest.beans.UpdateState;
import io.apicurio.registry.rest.beans.VersionMetaData;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.RegistryServiceTest;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.BaseIT;
import io.apicurio.tests.utils.subUtils.ArtifactUtils;
import io.vertx.core.json.JsonObject;

@Tag(SMOKE)
class ArtifactsIT extends BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtifactsIT.class);

    @RegistryServiceTest(localOnly = false)
    void createAndUpdateArtifact(RegistryService service) throws Exception {
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("FULL");

        LOGGER.info("Creating global rule:{}", rule.toString());
        service.createGlobalRule(rule);

        // Make sure we have rule
        TestUtils.retry(() -> service.getGlobalRuleConfig(rule.getType()));

        String artifactId = TestUtils.generateArtifactId();

        ByteArrayInputStream artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}".getBytes(StandardCharsets.UTF_8));
        ArtifactMetaData metaData = ArtifactUtils.createArtifact(service, ArtifactType.AVRO, artifactId, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData.toString());
        // Make sure artifact is fully registered
        ArtifactMetaData amd1 = metaData;
        TestUtils.retry(() -> service.getArtifactMetaDataByGlobalId(amd1.getGlobalId()));

        JsonObject response = new JsonObject(service.getLatestArtifact(artifactId).readEntity(String.class));

        LOGGER.info("Artifact with name:{} and content:{} was created", response.getString("name"), response);

        String invalidArtifactDefinition = "<type>record</type>\n<name>test</name>";
        artifactData = new ByteArrayInputStream(invalidArtifactDefinition.getBytes(StandardCharsets.UTF_8));
        String invalidArtifactId = "createAndUpdateArtifactId2";

        LOGGER.info("Invalid artifact sent {}", invalidArtifactDefinition);
        ByteArrayInputStream iad = artifactData;
        TestUtils.assertWebError(400, () -> ArtifactUtils.createArtifact(service, ArtifactType.AVRO, invalidArtifactId, iad));

        artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"long\"}]}".getBytes(StandardCharsets.UTF_8));
        metaData = ArtifactUtils.updateArtifact(service, ArtifactType.AVRO, artifactId, artifactData);
        LOGGER.info("Artifact with ID {} was updated: {}", artifactId, metaData.toString());
        // Make sure artifact is fully registered
        ArtifactMetaData amd2 = metaData;
        TestUtils.retry(() -> service.getArtifactMetaDataByGlobalId(amd2.getGlobalId()));

        response = new JsonObject(service.getLatestArtifact(artifactId).readEntity(String.class));

        LOGGER.info("Artifact with ID {} was updated: {}", artifactId, response);

        List<Long> apicurioVersions = service.listArtifactVersions(artifactId);

        LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId, apicurioVersions.toString());
        assertThat(apicurioVersions, hasItems(1L, 2L));

        response = new JsonObject(service.getArtifactVersion(1, artifactId).readEntity(String.class));

        LOGGER.info("Artifact with ID {} and version {}: {}", artifactId, 1, response);

        assertThat(response.getJsonArray("fields").getJsonObject(0).getString("name"), is("foo"));
    }

    @RegistryServiceTest(localOnly = false)
    void createAndDeleteMultipleArtifacts(RegistryService service) throws Exception {
        LOGGER.info("Creating some artifacts...");
        Map<String, String> idMap = createMultipleArtifacts(service, 10);
        LOGGER.info("Created  {} artifacts", idMap.size());

        deleteMultipleArtifacts(service, idMap);

        for (Map.Entry<String, String> entry : idMap.entrySet()) {
            TestUtils.assertWebError(404, () -> service.getLatestArtifact(entry.getValue()), true);
        }
    }

    @RegistryServiceTest(localOnly = false)
    void createNonAvroArtifact(RegistryService service) throws Exception {
        ByteArrayInputStream artifactData = new ByteArrayInputStream("{\"type\":\"INVALID\",\"config\":\"invalid\"}".getBytes(StandardCharsets.UTF_8));
        String artifactId = TestUtils.generateArtifactId();

        CompletionStage<ArtifactMetaData> csResult = service.createArtifact(ArtifactType.JSON, artifactId, null, artifactData);
        // Make sure artifact is fully registered
        ArtifactMetaData amd = ConcurrentUtil.result(csResult);
        TestUtils.retry(() -> service.getArtifactMetaDataByGlobalId(amd.getGlobalId()));

        LOGGER.info("Created artifact {} with metadata {}", artifactId, amd);

        JsonObject response = new JsonObject(service.getLatestArtifact(artifactId).readEntity(String.class));

        LOGGER.info("Got info about artifact with ID {}: {}", artifactId, response);
        assertThat(response.getString("type"), is("INVALID"));
        assertThat(response.getString("config"), is("invalid"));
    }

    @RegistryServiceTest(localOnly = false)
    void createArtifactSpecificVersion(RegistryService service) throws Exception {
        ByteArrayInputStream artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}".getBytes(StandardCharsets.UTF_8));
        String artifactId = TestUtils.generateArtifactId();
        ArtifactMetaData metaData = ArtifactUtils.createArtifact(service, ArtifactType.AVRO, artifactId, artifactData);
        // Make sure artifact is fully registered
        ArtifactMetaData amd1 = metaData;
        TestUtils.retry(() -> service.getArtifactMetaDataByGlobalId(amd1.getGlobalId()));
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData);

        artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}".getBytes(StandardCharsets.UTF_8));
        metaData = ArtifactUtils.updateArtifact(service, ArtifactType.AVRO, artifactId, artifactData);
        // Make sure artifact is fully updated
        ArtifactMetaData amd2 = metaData;
        TestUtils.retry(() -> service.getArtifactMetaDataByGlobalId(amd2.getGlobalId()));
        LOGGER.info("Artifact with ID {} was updated: {}", artifactId, metaData);

        List<Long> artifactVersions = service.listArtifactVersions(artifactId);

        LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId, artifactVersions.toString());
        assertThat(artifactVersions, hasItems(1L, 2L));
    }

    @RegistryServiceTest(localOnly = false)
    void testDuplicatedArtifact(RegistryService service) throws Exception {
        ByteArrayInputStream artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}".getBytes(StandardCharsets.UTF_8));
        String artifactId = TestUtils.generateArtifactId();
        ArtifactMetaData metaData = ArtifactUtils.createArtifact(service, ArtifactType.AVRO, artifactId, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData.toString());

        ByteArrayInputStream iad = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"alreadyExistArtifact\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}".getBytes(StandardCharsets.UTF_8));
        TestUtils.assertWebError(409, () -> ArtifactUtils.createArtifact(service, ArtifactType.AVRO, artifactId, iad), true);
    }

    @RegistryServiceTest(localOnly = false)
    void testDisableEnableArtifact(RegistryService service) throws Exception {
        String artifactId = TestUtils.generateArtifactId();
        String artifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        // Create the artifact
        ArtifactMetaData metaData = ArtifactUtils.createArtifact(service, ArtifactType.AVRO, artifactId, IoUtil.toStream(artifactData));
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData.toString());

        // Verify
        TestUtils.retry(() -> {
            ArtifactMetaData actualMD = service.getArtifactMetaData(artifactId);
            assertEquals(metaData.getGlobalId(), actualMD.getGlobalId());
        });

        // Disable the artifact
        UpdateState data = new UpdateState();
        data.setState(ArtifactState.DISABLED);
        service.updateArtifactState(artifactId, data);
        
        // Verify (expect 404)
        TestUtils.assertWebError(404, () -> service.getArtifactMetaData(artifactId), true);

        // Re-enable the artifact
        data.setState(ArtifactState.ENABLED);
        service.updateArtifactState(artifactId, data);
        
        // Verify
        TestUtils.retry(() -> {
            ArtifactMetaData actualMD = service.getArtifactMetaData(artifactId);
            assertEquals(metaData.getGlobalId(), actualMD.getGlobalId());
        });
    }

    @RegistryServiceTest(localOnly = false)
    void testDisableEnableArtifactVersion(RegistryService service) throws Exception {
        String artifactId = TestUtils.generateArtifactId();
        String artifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        String artifactDataV2 = "{\"type\":\"record\",\"name\":\"myrecord2\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        String artifactDataV3 = "{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        // Create the artifact
        ArtifactMetaData v1MD = ArtifactUtils.createArtifact(service, ArtifactType.AVRO, artifactId, IoUtil.toStream(artifactData));
        LOGGER.info("Created artifact {} with metadata {}", artifactId, v1MD.toString());
        TestUtils.retry(() -> service.getArtifactMetaDataByGlobalId(v1MD.getGlobalId()));

        // Update the artifact (v2)
        ArtifactMetaData v2MD = ArtifactUtils.updateArtifact(service, ArtifactType.AVRO, artifactId, IoUtil.toStream(artifactDataV2));
        TestUtils.retry(() -> service.getArtifactMetaDataByGlobalId(v2MD.getGlobalId()));

        // Update the artifact (v3)
        ArtifactMetaData v3MD = ArtifactUtils.updateArtifact(service, ArtifactType.AVRO, artifactId, IoUtil.toStream(artifactDataV3));
        TestUtils.retry(() -> service.getArtifactMetaDataByGlobalId(v3MD.getGlobalId()));

        // Disable v3
        UpdateState data = new UpdateState();
        data.setState(ArtifactState.DISABLED);
        service.updateArtifactVersionState(v3MD.getVersion(), artifactId, data);

        // Verify artifact
        TestUtils.retry(() -> {
            ArtifactMetaData actualMD = service.getArtifactMetaData(artifactId);
            assertEquals(ArtifactState.ENABLED, actualMD.getState());
            assertEquals(2, actualMD.getVersion()); // version 2 is active (3 is disabled)

            // Verify v1
            VersionMetaData actualVMD = service.getArtifactVersionMetaData(v1MD.getVersion(), artifactId);
            assertEquals(ArtifactState.ENABLED, actualVMD.getState());
            // Verify v2
            actualVMD = service.getArtifactVersionMetaData(v2MD.getVersion(), artifactId);
            assertEquals(ArtifactState.ENABLED, actualVMD.getState());
            // Verify v3
            actualVMD = service.getArtifactVersionMetaData(v3MD.getVersion(), artifactId);
            assertEquals(ArtifactState.DISABLED, actualVMD.getState());
        });
        
        // Re-enable v3
        data.setState(ArtifactState.ENABLED);
        service.updateArtifactVersionState(v3MD.getVersion(), artifactId, data);

        TestUtils.retry(() -> {
            // Verify artifact (now v3)
            ArtifactMetaData actualMD = service.getArtifactMetaData(artifactId);
            assertEquals(ArtifactState.ENABLED, actualMD.getState());
            assertEquals(3, actualMD.getVersion()); // version 2 is active (3 is disabled)

            // Verify v1
            VersionMetaData actualVMD = service.getArtifactVersionMetaData(v1MD.getVersion(), artifactId);
            assertEquals(ArtifactState.ENABLED, actualVMD.getState());
            // Verify v2
            actualVMD = service.getArtifactVersionMetaData(v2MD.getVersion(), artifactId);
            assertEquals(ArtifactState.ENABLED, actualVMD.getState());
            // Verify v3
            actualVMD = service.getArtifactVersionMetaData(v3MD.getVersion(), artifactId);
            assertEquals(ArtifactState.ENABLED, actualVMD.getState());
        });
    }

    @RegistryServiceTest(localOnly = false)
    void testDeprecateArtifact(RegistryService service) throws Exception {
        String artifactId = TestUtils.generateArtifactId();
        String artifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        // Create the artifact
        ArtifactMetaData metaData = ArtifactUtils.createArtifact(service, ArtifactType.AVRO, artifactId, IoUtil.toStream(artifactData));
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData.toString());

        TestUtils.retry(() -> {
            // Verify
            ArtifactMetaData actualMD = service.getArtifactMetaData(artifactId);
            assertEquals(metaData.getGlobalId(), actualMD.getGlobalId());
            assertEquals(ArtifactState.ENABLED, actualMD.getState());
        });

        // Deprecate the artifact
        UpdateState data = new UpdateState();
        data.setState(ArtifactState.DEPRECATED);
        service.updateArtifactState(artifactId, data);

        TestUtils.retry(() -> {
            // Verify (expect 404)
            ArtifactMetaData actualMD = service.getArtifactMetaData(artifactId);
            assertEquals(metaData.getGlobalId(), actualMD.getGlobalId());
            assertEquals(ArtifactState.DEPRECATED, actualMD.getState());
        });
    }

    @RegistryServiceTest(localOnly = false)
    void testDeprecateArtifactVersion(RegistryService service) throws Exception {
        String artifactId = TestUtils.generateArtifactId();
        String artifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        String artifactDataV2 = "{\"type\":\"record\",\"name\":\"myrecord2\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        String artifactDataV3 = "{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        // Create the artifact
        ArtifactMetaData v1MD = ArtifactUtils.createArtifact(service, ArtifactType.AVRO, artifactId, IoUtil.toStream(artifactData));
        LOGGER.info("Created artifact {} with metadata {}", artifactId, v1MD.toString());
        TestUtils.retry(() -> service.getArtifactMetaDataByGlobalId(v1MD.getGlobalId()));

        // Update the artifact (v2)
        ArtifactMetaData v2MD = ArtifactUtils.updateArtifact(service, ArtifactType.AVRO, artifactId, IoUtil.toStream(artifactDataV2));
        TestUtils.retry(() -> service.getArtifactMetaDataByGlobalId(v2MD.getGlobalId()));

        // Update the artifact (v3)
        ArtifactMetaData v3MD = ArtifactUtils.updateArtifact(service, ArtifactType.AVRO, artifactId, IoUtil.toStream(artifactDataV3));
        TestUtils.retry(() -> service.getArtifactMetaDataByGlobalId(v3MD.getGlobalId()));

        // Deprecate v2
        UpdateState data = new UpdateState();
        data.setState(ArtifactState.DEPRECATED);
        service.updateArtifactVersionState(v2MD.getVersion(), artifactId, data);

        TestUtils.retry(() -> {
            // Verify artifact
            ArtifactMetaData actualMD = service.getArtifactMetaData(artifactId);
            assertEquals(ArtifactState.ENABLED, actualMD.getState());

            // Verify v1
            VersionMetaData actualVMD = service.getArtifactVersionMetaData(v1MD.getVersion(), artifactId);
            assertEquals(ArtifactState.ENABLED, actualVMD.getState());
            // Verify v2
            actualVMD = service.getArtifactVersionMetaData(v2MD.getVersion(), artifactId);
            assertEquals(ArtifactState.DEPRECATED, actualVMD.getState());
            // Verify v3
            actualVMD = service.getArtifactVersionMetaData(v3MD.getVersion(), artifactId);
            assertEquals(ArtifactState.ENABLED, actualVMD.getState());
        });
    }

    @RegistryServiceTest(localOnly = false)
    void deleteNonexistingSchema(RegistryService service) {
        TestUtils.assertWebError(404, () -> service.deleteArtifact("non-existing"));
    }

    @AfterEach
    void deleteRules(RegistryService service) {
        service.deleteAllGlobalRules();
    }
}

