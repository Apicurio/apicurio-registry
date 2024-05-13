package io.apicurio.tests.smokeTests.apicurio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.rest.client.models.ArtifactSearchResults;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.EditableVersionMetaData;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.SortBy;
import io.apicurio.registry.rest.client.models.SortOrder;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.client.models.VersionState;
import io.apicurio.registry.rest.v2.beans.ArtifactContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.utils.AvroGenericRecordSchemaFactory;
import io.apicurio.tests.utils.Constants;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

        CreateArtifactResponse caResponse = createArtifact(groupId, artifactId, ArtifactType.AVRO, artifactData, ContentTypes.APPLICATION_JSON, IfArtifactExists.FAIL, null);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, caResponse.getArtifact().toString());

        InputStream latest = registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("branch=latest").content().get();
        JsonNode response = mapper.readTree(latest);

        LOGGER.info("Artifact with name:{} and content:{} was created", response.get("name").asText(), response);

        String invalidArtifactDefinition = "<type>record</type>\n<name>test</name>";
        String invalidArtifactId = "createAndUpdateArtifactId2";

        LOGGER.info("Invalid artifact sent {}", invalidArtifactDefinition);
        assertClientError("RuleViolationException", 409, () ->
                registryClient.groups().byGroupId("ccc").artifacts().post(
                        TestUtils.clientCreateArtifact(invalidArtifactId, ArtifactType.AVRO, invalidArtifactDefinition, ContentTypes.APPLICATION_JSON)
                ), errorCodeExtractor);

        artifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"long\"}]}";
        CreateVersion createVersion = TestUtils.clientCreateVersion(artifactData, ContentTypes.APPLICATION_JSON);
        VersionMetaData metaData = registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().post(createVersion);
        LOGGER.info("Artifact with ID {} was updated: {}", artifactId, metaData.toString());
        // Make sure artifact is fully registered
        retryOp((rc) -> rc.ids().globalIds().byGlobalId(metaData.getGlobalId()).get());

        latest = registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("branch=latest").content().get();
        response = mapper.readTree(latest);

        LOGGER.info("Artifact with ID {} was updated: {}", artifactId, response);

        List<String> apicurioVersions = listArtifactVersions(registryClient, groupId, artifactId);

        LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId, apicurioVersions.toString());
        assertThat(apicurioVersions, hasItems("1", "2"));

        InputStream version1 = registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("1").content().get();
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
                        String content = new AvroGenericRecordSchemaFactory(groupId, artifactId, List.of("foo")).generateSchema().toString();
                        String ct = ContentTypes.APPLICATION_JSON;
                        return createArtifact(groupId, artifactId, ArtifactType.AVRO, content, ct, null, null).getVersion();
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
        String artifactData = "{\"type\":\"INVALID\",\"config\":\"invalid\"}";
        String artifactId = TestUtils.generateArtifactId();

        var caResponse = createArtifact(groupId, artifactId, ArtifactType.JSON, artifactData, ContentTypes.APPLICATION_JSON, null, null);

        LOGGER.info("Created artifact {} with metadata {}", artifactId, caResponse.getArtifact());

        InputStream latest = registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("branch=latest").content().get();
        JsonNode response = mapper.readTree(latest);

        LOGGER.info("Got info about artifact with ID {}: {}", artifactId, response);
        assertThat(response.get("type").asText(), is("INVALID"));
        assertThat(response.get("config").asText(), is("invalid"));
    }

    @Test
    void createArtifactSpecificVersion() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        String artifactId = TestUtils.generateArtifactId();
        var caResponse = createArtifact(groupId, artifactId, ArtifactType.AVRO, artifactData, ContentTypes.APPLICATION_JSON, null, null);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, caResponse.getArtifact());

        artifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}";
        var metaData = createArtifactVersion(groupId, artifactId, artifactData, ContentTypes.APPLICATION_JSON, null);
        LOGGER.info("Artifact with ID {} was updated: {}", artifactId, metaData);

        List<String> artifactVersions = listArtifactVersions(registryClient, groupId, artifactId);

        LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId, artifactVersions.toString());
        assertThat(artifactVersions, hasItems("1", "2"));
    }

    @Test
    @Tag(ACCEPTANCE)
    void testDuplicatedArtifact() throws Exception {
        String artifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        var caResponse = createArtifact(groupId, artifactId, ArtifactType.AVRO, artifactData, ContentTypes.APPLICATION_JSON, null, null);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, caResponse.getArtifact().toString());

        String invalidArtifactData = "{\"type\":\"record\",\"name\":\"alreadyExistArtifact\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        assertClientError("ArtifactAlreadyExistsException", 409, () ->
                createArtifact(groupId, artifactId, ArtifactType.AVRO, invalidArtifactData, ContentTypes.APPLICATION_JSON, null, null), true, errorCodeExtractor);
    }

    @Test
    @Tag(ACCEPTANCE)
    void testVersionAlreadyExistsIfExistsCreateVersion() throws Exception {
        String artifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        var caResponse = createArtifact(groupId, artifactId, ArtifactType.AVRO, artifactData, ContentTypes.APPLICATION_JSON, null, (ca) -> {
            ca.getFirstVersion().setVersion("1.1");
            return null;
        });

        LOGGER.info("Created artifact {} with metadata {}", artifactId, caResponse.getArtifact().toString());

        String sameArtifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        assertClientError("VersionAlreadyExistsException", 409, () ->
                createArtifact(groupId, artifactId, ArtifactType.AVRO, sameArtifactData, ContentTypes.APPLICATION_JSON, IfArtifactExists.CREATE_VERSION, (ca) -> {
                    ca.getFirstVersion().setVersion("1.1");
                    return null;
                }), true, errorCodeExtractor);
    }

    @Test
    void testDisableEnableArtifactVersion() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        String artifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        String artifactDataV2 = "{\"type\":\"record\",\"name\":\"myrecord2\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        String artifactDataV3 = "{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        // Create the artifact
        VersionMetaData v1MD = createArtifact(groupId, artifactId, ArtifactType.AVRO, artifactData, ContentTypes.APPLICATION_JSON, null, null).getVersion();
        LOGGER.info("Created artifact {} with metadata {}", artifactId, v1MD.toString());

        // Update the artifact (v2)
        VersionMetaData v2MD = createArtifactVersion(groupId, artifactId, artifactDataV2, ContentTypes.APPLICATION_JSON, null);

        // Update the artifact (v3)
        VersionMetaData v3MD = createArtifactVersion(groupId, artifactId, artifactDataV3, ContentTypes.APPLICATION_JSON, null);

        // Disable v3
        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression(String.valueOf(v3MD.getVersion())).put(toEditableVersionMetaData(VersionState.DISABLED));

        // Verify artifact
        retryOp((rc) -> {
            VersionMetaData actualMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("branch=latest").get();
            assertEquals("2", actualMD.getVersion());

            // Verify v1
            VersionMetaData actualVMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression(String.valueOf(v1MD.getVersion())).get();
            assertEquals(VersionState.ENABLED, actualVMD.getState());
            // Verify v2
            actualVMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression(String.valueOf(v2MD.getVersion())).get();
            assertEquals(VersionState.ENABLED, actualVMD.getState());
            // Verify v3
            actualVMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression(String.valueOf(v3MD.getVersion())).get();
            assertEquals(VersionState.DISABLED, actualVMD.getState());
        });

        // Re-enable v3
        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression(String.valueOf(v3MD.getVersion())).put(toEditableVersionMetaData(VersionState.ENABLED));

        retryOp((rc) -> {
            // Verify artifact (now v3)
            VersionMetaData actualMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("branch=latest").get();
            assertEquals("3", actualMD.getVersion()); // version 2 is active (3 is disabled)

            // Verify v1
            VersionMetaData actualVMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression(String.valueOf(v1MD.getVersion())).get();
            assertEquals(VersionState.ENABLED, actualVMD.getState());
            // Verify v2
            actualVMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression(String.valueOf(v2MD.getVersion())).get();
            assertEquals(VersionState.ENABLED, actualVMD.getState());
            // Verify v3
            actualVMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression(String.valueOf(v3MD.getVersion())).get();
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
        VersionMetaData v1MD = createArtifact(groupId, artifactId, ArtifactType.AVRO, artifactData, ContentTypes.APPLICATION_JSON, null, null).getVersion();
        LOGGER.info("Created artifact {} with metadata {}", artifactId, v1MD.toString());

        // Update the artifact (v2)
        VersionMetaData v2MD = createArtifactVersion(groupId, artifactId, artifactDataV2, ContentTypes.APPLICATION_JSON, null);

        // Update the artifact (v3)
        VersionMetaData v3MD = createArtifactVersion(groupId, artifactId, artifactDataV3, ContentTypes.APPLICATION_JSON, null);

        // Deprecate v2
        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression(String.valueOf(v2MD.getVersion())).put(toEditableVersionMetaData(VersionState.DEPRECATED));

        retryOp((rc) -> {
            // Verify v1
            VersionMetaData actualVMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression(String.valueOf(v1MD.getVersion())).get();
            assertEquals(VersionState.ENABLED, actualVMD.getState());
            // Verify v2
            actualVMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression(String.valueOf(v2MD.getVersion())).get();
            assertEquals(VersionState.DEPRECATED, actualVMD.getState());
            // Verify v3
            actualVMD = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression(String.valueOf(v3MD.getVersion())).get();
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
        createArtifact(groupId, artifactId, ArtifactType.AVRO, content, ContentTypes.APPLICATION_JSON, null, null);

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();

        VersionContent vc = new VersionContent();
        vc.setContent(content);
        vc.setContentType(ContentTypes.APPLICATION_JSON);
        retryOp((rc) -> rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).post(vc));

        registryClient.search().artifacts().get(config -> {
            config.queryParameters.group = groupId;
            config.queryParameters.name = artifactId;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
        });

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).test().put(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), "application/create.extended+json");

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().post(
                TestUtils.clientCreateVersion(content, ContentTypes.APPLICATION_JSON)
        );

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

        createArtifact(groupId, artifactId, ArtifactType.AVRO, content, ContentTypes.APPLICATION_JSON, null, null);

        retryOp((rc) -> rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get());

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();

        VersionContent vc = new VersionContent();
        vc.setContent(content);
        vc.setContentType(ContentTypes.APPLICATION_JSON);
        retryOp((rc) -> rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).post(vc));

        registryClient.search().artifacts().get(config -> {
            config.queryParameters.group = groupId;
            config.queryParameters.name = artifactId;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 100;
        });

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).test().put(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), "application/create.extended+json");

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().post(
                TestUtils.clientCreateVersion(content, ContentTypes.APPLICATION_JSON)
        );

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
            this.createArtifact(group, artifactId, ArtifactType.AVRO, content, ContentTypes.APPLICATION_JSON, null, null);
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

