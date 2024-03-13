package io.apicurio.tests.smokeTests.apicurio;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
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

import io.apicurio.registry.rest.client.models.ArtifactContent;
import io.apicurio.registry.rest.client.models.ArtifactSearchResults;
import io.apicurio.registry.rest.client.models.EditableVersionMetaData;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.SortBy;
import io.apicurio.registry.rest.client.models.SortOrder;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.client.models.VersionState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.utils.AvroGenericRecordSchemaFactory;
import io.apicurio.tests.utils.Constants;
import io.quarkus.test.junit.QuarkusIntegrationTest;

@Tag(Constants.SMOKE)
@QuarkusIntegrationTest
class ArtifactsIT extends ApicurioRegistryBaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtifactsIT.class);

    private final ObjectMapper mapper = new ObjectMapper();

    private static final EditableVersionMetaData toEditableVersionMetaData(VersionState state) {
        EditableVersionMetaData evmd = new EditableVersionMetaData();
        evmd.setState(state);
        return evmd;
    }

    @Test
    @Tag(ACCEPTANCE)
    void createAndUpdateArtifact() throws Exception {
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("FULL");

        LOGGER.info("Creating global rule:{}", rule.toString());
        registryClient.admin().rules().post(rule);

        // Make sure we have rule
        retryOp((rc) -> rc.admin().rules().byRule(rule.getType().name()).get());

        String groupId = TestUtils.generateGroupId();

        String artifactId = TestUtils.generateArtifactId();

        var artifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        VersionMetaData amd1 = createArtifact(groupId, artifactId, ArtifactType.AVRO, IoUtil.toStream(artifactData));
        LOGGER.info("Created artifact {} with metadata {}", artifactId, amd1.toString());

        InputStream latest = registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("branch=latest").get();
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
                }), errorCodeExtractor);

        artifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"long\"}]}";
        content.setContent(artifactData);
        VersionMetaData metaData = registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().post(content);
        LOGGER.info("Artifact with ID {} was updated: {}", artifactId, metaData.toString());
        // Make sure artifact is fully registered
        retryOp((rc) -> rc.ids().globalIds().byGlobalId(metaData.getGlobalId()).get());

        latest = registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("branch=latest").get();
        response = mapper.readTree(latest);

        LOGGER.info("Artifact with ID {} was updated: {}", artifactId, response);

        List<String> apicurioVersions = listArtifactVersions(registryClient, groupId, artifactId);

        LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId, apicurioVersions.toString());
        assertThat(apicurioVersions, hasItems("1", "2"));

        InputStream version1 = registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("1").get();
        response = mapper.readTree(version1);

        LOGGER.info("Artifact with ID {} and version {}: {}", artifactId, 1, response);

        assertThat(response.get("fields").elements().next().get("name").asText(), is("foo"));
    }

    @Test
    void createAndDeleteMultipleArtifacts() throws Exception {
        LOGGER.info("Creating some artifacts...");
        String groupId = TestUtils.generateGroupId();

        List<VersionMetaData> artifacts = IntStream.range(0, 10)
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
        registryClient.groups().byGroupId(groupId).delete();

        for (VersionMetaData artifact : artifacts) {
            retryAssertClientError("ArtifactNotFoundException", 404, (rc) ->
                    rc.groups().byGroupId(artifact.getGroupId()).artifacts().byArtifactId(artifact.getArtifactId()).get(), errorCodeExtractor);
        }
    }

    @Test
    @Tag(ACCEPTANCE)
    void createNonAvroArtifact() throws Exception {
        String groupId = TestUtils.generateArtifactId();
        ByteArrayInputStream artifactData = new ByteArrayInputStream("{\"type\":\"INVALID\",\"config\":\"invalid\"}".getBytes(StandardCharsets.UTF_8));
        String artifactId = TestUtils.generateArtifactId();

        VersionMetaData amd = createArtifact(groupId, artifactId, ArtifactType.JSON, artifactData);

        LOGGER.info("Created artifact {} with metadata {}", artifactId, amd);

        InputStream latest = registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("branch=latest").get();
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
        VersionMetaData metaData = createArtifact(groupId, artifactId, ArtifactType.AVRO, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData);

        artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}".getBytes(StandardCharsets.UTF_8));
        metaData = createArtifactVersion(groupId, artifactId, artifactData);
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
        VersionMetaData metaData = createArtifact(groupId, artifactId, ArtifactType.AVRO, artifactData);
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
        VersionMetaData metaData = createArtifact(groupId, artifactId, "1.1", "FAIL", ArtifactType.AVRO, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData.toString());

        ByteArrayInputStream sameArtifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}".getBytes(StandardCharsets.UTF_8));

        assertClientError("VersionAlreadyExistsException", 409, () -> createArtifact(groupId, artifactId, "1.1", "UPDATE", ArtifactType.AVRO, sameArtifactData), true, errorCodeExtractor);
    }

    @Test
    void testDisableEnableArtifactVersion() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        String artifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        String artifactDataV2 = "{\"type\":\"record\",\"name\":\"myrecord2\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        String artifactDataV3 = "{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        // Create the artifact
        VersionMetaData v1MD = createArtifact(groupId, artifactId, ArtifactType.AVRO, IoUtil.toStream(artifactData));
        LOGGER.info("Created artifact {} with metadata {}", artifactId, v1MD.toString());

        // Update the artifact (v2)
        VersionMetaData v2MD = createArtifactVersion(groupId, artifactId, IoUtil.toStream(artifactDataV2));

        // Update the artifact (v3)
        VersionMetaData v3MD = createArtifactVersion(groupId, artifactId, IoUtil.toStream(artifactDataV3));

        // Disable v3
        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression(String.valueOf(v3MD.getVersion())).meta().put(toEditableVersionMetaData(VersionState.DISABLED));

        // Verify artifact
        retryOp((rc) -> {
            VersionMetaData actualMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("branch=latest").meta().get();
            assertEquals("2", actualMD.getVersion());

            // Verify v1
            VersionMetaData actualVMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression(String.valueOf(v1MD.getVersion())).meta().get();
            assertEquals(VersionState.ENABLED, actualVMD.getState());
            // Verify v2
            actualVMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression(String.valueOf(v2MD.getVersion())).meta().get();
            assertEquals(VersionState.ENABLED, actualVMD.getState());
            // Verify v3
            actualVMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression(String.valueOf(v3MD.getVersion())).meta().get();
            assertEquals(VersionState.DISABLED, actualVMD.getState());
        });

        // Re-enable v3
        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression(String.valueOf(v3MD.getVersion())).meta().put(toEditableVersionMetaData(VersionState.ENABLED));

        retryOp((rc) -> {
            // Verify artifact (now v3)
            VersionMetaData actualMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("branch=latest").meta().get();
            assertEquals("3", actualMD.getVersion()); // version 2 is active (3 is disabled)

            // Verify v1
            VersionMetaData actualVMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression(String.valueOf(v1MD.getVersion())).meta().get();
            assertEquals(VersionState.ENABLED, actualVMD.getState());
            // Verify v2
            actualVMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression(String.valueOf(v2MD.getVersion())).meta().get();
            assertEquals(VersionState.ENABLED, actualVMD.getState());
            // Verify v3
            actualVMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression(String.valueOf(v3MD.getVersion())).meta().get();
            assertEquals(VersionState.ENABLED, actualVMD.getState());
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
        VersionMetaData v1MD = createArtifact(groupId, artifactId, ArtifactType.AVRO, IoUtil.toStream(artifactData));
        LOGGER.info("Created artifact {} with metadata {}", artifactId, v1MD.toString());

        // Update the artifact (v2)
        VersionMetaData v2MD = createArtifactVersion(groupId, artifactId, IoUtil.toStream(artifactDataV2));

        // Update the artifact (v3)
        VersionMetaData v3MD = createArtifactVersion(groupId, artifactId, IoUtil.toStream(artifactDataV3));

        // Deprecate v2
        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression(String.valueOf(v2MD.getVersion())).meta().put(toEditableVersionMetaData(VersionState.DEPRECATED));

        retryOp((rc) -> {
            // Verify v1
            VersionMetaData actualVMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression(String.valueOf(v1MD.getVersion())).meta().get();
            assertEquals(VersionState.ENABLED, actualVMD.getState());
            // Verify v2
            actualVMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression(String.valueOf(v2MD.getVersion())).meta().get();
            assertEquals(VersionState.DEPRECATED, actualVMD.getState());
            // Verify v3
            actualVMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression(String.valueOf(v3MD.getVersion())).meta().get();
            assertEquals(VersionState.ENABLED, actualVMD.getState());
        });
    }

    @Test
    void deleteNonexistingSchema() throws Exception {
        assertClientError("ArtifactNotFoundException", 404, () ->
                registryClient.groups().byGroupId("nonn-existent-group").artifacts().byArtifactId("non-existing").get(), errorCodeExtractor);
    }

    @Test
    void testAllowedSpecialCharacters() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "._:-'`?0=)(/&$!<>,;,:";

        String content = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        ArtifactContent artifactContent = new ArtifactContent();
        artifactContent.setContent(content);
        createArtifact(groupId, artifactId, ArtifactType.AVRO, IoUtil.toStream(content));

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();

        retryOp((rc) -> rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).post(artifactContent));

        registryClient.search().artifacts().get(config -> {
            config.queryParameters.group = groupId;
            config.queryParameters.name = artifactId;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
        });

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).test().put(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), "application/create.extended+json");

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().post(artifactContent);

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();

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

        retryOp((rc) -> rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get());

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();

        retryOp((rc) -> rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).post(artifactContent));

        registryClient.search().artifacts().get(config -> {
            config.queryParameters.group = groupId;
            config.queryParameters.name = artifactId;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 100;
        });

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).test().put(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), "application/create.extended+json");

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().post(artifactContent);

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
            config.queryParameters.order = SortOrder.Asc;
            config.queryParameters.orderby = SortBy.CreatedOn;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
        });

        Assertions.assertNotNull(results);
        Assertions.assertEquals(5, results.getCount());
        Assertions.assertEquals(5, results.getArtifacts().size());
        Assertions.assertEquals("test-0", results.getArtifacts().get(0).getArtifactId());

    }

    @AfterEach
    void deleteRules() throws Exception {
        try {
            registryClient.admin().rules().delete();
            retryOp((rc) -> {
                List<RuleType> rules = rc.admin().rules().get();
                assertEquals(0, rules.size(), "All global rules not deleted");
            });
        } catch (Exception e) {
            // ignore
        }
    }
}

