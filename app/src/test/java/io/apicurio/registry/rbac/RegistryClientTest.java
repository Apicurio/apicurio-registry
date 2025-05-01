package io.apicurio.registry.rbac;

import com.microsoft.kiota.ApiException;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.ArtifactSearchResults;
import io.apicurio.registry.rest.client.models.ArtifactSortBy;
import io.apicurio.registry.rest.client.models.ConfigurationProperty;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.EditableArtifactMetaData;
import io.apicurio.registry.rest.client.models.GroupMetaData;
import io.apicurio.registry.rest.client.models.GroupSearchResults;
import io.apicurio.registry.rest.client.models.GroupSortBy;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.RoleMapping;
import io.apicurio.registry.rest.client.models.RoleType;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.SearchedArtifact;
import io.apicurio.registry.rest.client.models.SearchedGroup;
import io.apicurio.registry.rest.client.models.SortOrder;
import io.apicurio.registry.rest.client.models.UpdateConfigurationProperty;
import io.apicurio.registry.rest.client.models.UpdateRole;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.rest.client.models.VersionState;
import io.apicurio.registry.rest.client.models.WrappedVersionState;
import io.apicurio.registry.rest.v2.beans.ArtifactContent;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.ApplicationRbacEnabledProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.registry.utils.tests.TooManyRequestsMock;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(ApplicationRbacEnabledProfile.class)
@SuppressWarnings("deprecation")
@Tag(ApicurioTestTags.SLOW)
public class RegistryClientTest extends AbstractResourceTestBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistryClientTest.class);

    private static final String ARTIFACT_OPENAPI_YAML_CONTENT = "openapi: \"3.0.2\"\n" + "info:\n"
            + "  description: \"Description\"\n" + "  version: \"1.0.0\"\n" + "  title: \"OpenAPI\"\n"
            + "paths:";
    private static final String UPDATED_OPENAPI_YAML_CONTENT = "openapi: \"3.0.2\"\n" + "info:\n"
            + "  description: \"Description v2\"\n" + "  version: \"2.0.0\"\n" + "  title: \"OpenAPI\"\n"
            + "paths:";

    private static final String ARTIFACT_OPENAPI_JSON_CONTENT = "{\n" + "  \"openapi\" : \"3.0.2\",\n"
            + "  \"info\" : {\n" + "    \"description\" : \"Description\",\n"
            + "    \"version\" : \"1.0.0\",\n" + "    \"title\" : \"OpenAPI\"\n" + "  },\n"
            + "  \"paths\" : null\n" + "}";
    private static final String UPDATED_OPENAPI_JSON_CONTENT = "{\n" + "  \"openapi\" : \"3.0.2\",\n"
            + "  \"info\" : {\n" + "    \"description\" : \"Description v2\",\n"
            + "    \"version\" : \"2.0.0\",\n" + "    \"title\" : \"OpenAPI\"\n" + "  },\n"
            + "  \"paths\" : null\n" + "}";

    private static final String SCHEMA_WITH_REFERENCE = "{\r\n    \"namespace\":\"com.example.common\",\r\n    \"name\":\"Item\",\r\n    \"type\":\"record\",\r\n    \"fields\":[\r\n        {\r\n            \"name\":\"itemId\",\r\n            \"type\":\"com.example.common.ItemId\"\r\n        }]\r\n}";
    private static final String REFERENCED_SCHEMA = "{\"namespace\": \"com.example.common\", \"type\": \"record\", \"name\": \"ItemId\", \"fields\":[{\"name\":\"id\", \"type\":\"int\"}]}";

    private static final String ARTIFACT_CONTENT = "{\"name\":\"redhat\"}";
    private static final String UPDATED_CONTENT = "{\"name\":\"ibm\"}";

    @Inject
    MockAuditLogService auditLogService;

    @Test
    public void testCreateArtifact() throws Exception {
        // Preparation
        final String groupId = "testCreateArtifact";
        final String artifactId = generateArtifactId();

        final String version = "1";
        final String name = "testCreateArtifactName";
        final String description = "testCreateArtifactDescription";

        // Execution
        CreateArtifactResponse created = createArtifact(groupId, artifactId, ArtifactType.JSON,
                ARTIFACT_CONTENT, ContentTypes.APPLICATION_JSON, (createArtifact -> {
                    createArtifact.setName(name);
                    createArtifact.setDescription(description);
                    createArtifact.getFirstVersion().setVersion(version);
                }));

        // Assertions
        assertNotNull(created);
        assertEquals(groupId, created.getArtifact().getGroupId());
        assertEquals(artifactId, created.getArtifact().getArtifactId());
        assertEquals(version, created.getVersion().getVersion());
        assertEquals(name, created.getArtifact().getName());
        assertEquals(description, created.getArtifact().getDescription());
        assertEquals(ARTIFACT_CONTENT,
                new String(
                        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                                .byVersionExpression("branch=latest").content().get().readAllBytes(),
                        StandardCharsets.UTF_8));
    }

    @Test
    public void groupsCrud() throws Exception {
        // Preparation
        final String groupId = UUID.randomUUID().toString();
        CreateGroup groupMetaData = new CreateGroup();
        groupMetaData.setGroupId(groupId);
        groupMetaData.setDescription("Groups test crud");
        Labels labels = new Labels();
        labels.setAdditionalData(Map.of("p1", "v1", "p2", "v2"));
        groupMetaData.setLabels(labels);

        clientV3.groups().post(groupMetaData);

        final GroupMetaData artifactGroup = clientV3.groups().byGroupId(groupId).get();
        assertEquals(groupMetaData.getGroupId(), artifactGroup.getGroupId());
        assertEquals(groupMetaData.getDescription(), artifactGroup.getDescription());
        assertEquals(groupMetaData.getLabels().getAdditionalData(),
                artifactGroup.getLabels().getAdditionalData());

        String group1Id = UUID.randomUUID().toString();
        String group2Id = UUID.randomUUID().toString();
        String group3Id = UUID.randomUUID().toString();

        groupMetaData.setGroupId(group1Id);
        clientV3.groups().post(groupMetaData);
        groupMetaData.setGroupId(group2Id);
        clientV3.groups().post(groupMetaData);
        groupMetaData.setGroupId(group3Id);
        clientV3.groups().post(groupMetaData);

        GroupSearchResults groupSearchResults = clientV3.groups().get(config -> {
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 100;
            config.queryParameters.order = SortOrder.Asc;
            config.queryParameters.orderby = GroupSortBy.GroupId;
        });
        assertTrue(groupSearchResults.getCount() >= 4);

        final List<String> groupIds = groupSearchResults.getGroups().stream().map(SearchedGroup::getGroupId)
                .collect(Collectors.toList());

        assertTrue(groupIds.containsAll(List.of(groupId, group1Id, group2Id, group3Id)));
        clientV3.groups().byGroupId(groupId).delete();

        var exception = Assert.assertThrows(io.apicurio.registry.rest.client.models.ProblemDetails.class,
                () -> clientV3.groups().byGroupId(groupId).get());
        Assertions.assertEquals("GroupNotFoundException", exception.getName());
        Assertions.assertEquals(404, exception.getStatus());
    }

    @Test
    public void testCreateYamlArtifact() throws Exception {
        // Preparation
        final String groupId = "testCreateYamlArtifact";
        final String artifactId = generateArtifactId();

        final String version = "1";
        final String name = "testCreateYamlArtifactName";
        final String description = "testCreateYamlArtifactDescription";

        // Execution
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.OPENAPI,
                ARTIFACT_OPENAPI_YAML_CONTENT, ContentTypes.APPLICATION_JSON);
        createArtifact.setName(name);
        createArtifact.setDescription(description);
        createArtifact.getFirstVersion().setVersion(version);
        createArtifact.getFirstVersion().setName(name);
        createArtifact.getFirstVersion().setDescription(description);

        final VersionMetaData created = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact)
                .getVersion();

        // Assertions
        assertNotNull(created);
        assertEquals(groupId, created.getGroupId());
        assertEquals(artifactId, created.getArtifactId());
        assertEquals(version, created.getVersion());
        assertEquals(name, created.getName());
        assertEquals(description, created.getDescription());
        assertMultilineTextEquals(ARTIFACT_OPENAPI_YAML_CONTENT,
                IoUtil.toString(clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                        .versions().byVersionExpression("branch=latest").content().get()));
    }

    @Test
    public void testCreateArtifactVersion() throws Exception {
        // Preparation
        final String groupId = "testCreateArtifactVersion";
        final String artifactId = generateArtifactId();

        final String version = "2";
        final String name = "testCreateArtifactVersionName";
        final String description = "testCreateArtifactVersionDescription";

        createArtifact(groupId, artifactId);

        // Execution
        CreateVersion createVersion = TestUtils.clientCreateVersion(UPDATED_CONTENT,
                ContentTypes.APPLICATION_JSON);
        createVersion.setName(name);
        createVersion.setDescription(description);
        createVersion.setVersion(version);
        VersionMetaData versionMetaData = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().post(createVersion);

        ArtifactMetaData amd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .get();

        // Assertions
        assertNotNull(versionMetaData);
        assertEquals(version, versionMetaData.getVersion());
        assertEquals(name, versionMetaData.getName());
        assertEquals(description, versionMetaData.getDescription());

        assertNotNull(amd);
        assertEquals(artifactId, amd.getName());
        assertNull(amd.getDescription());

        assertEquals(UPDATED_CONTENT, IoUtil.toString(clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().byVersionExpression("branch=latest").content().get()));
    }

    @Test
    public void testCreateYamlArtifactVersion() throws Exception {
        // Preparation
        final String groupId = "testCreateYamlArtifactVersion";
        final String artifactId = generateArtifactId();

        final String version = "2";
        final String name = "testCreateYamlArtifactVersionName";
        final String description = "testCreateYamlArtifactVersionDescription";

        createOpenAPIArtifact(groupId, artifactId); // Create first version of the openapi artifact using JSON

        // Execution
        CreateVersion createVersion = TestUtils.clientCreateVersion(UPDATED_OPENAPI_YAML_CONTENT,
                ContentTypes.APPLICATION_JSON);
        createVersion.setName(name);
        createVersion.setDescription(description);
        createVersion.setVersion(version);

        ArtifactContent content = new ArtifactContent();
        content.setContent(UPDATED_OPENAPI_YAML_CONTENT);
        VersionMetaData versionMetaData = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().post(createVersion);
        VersionMetaData amd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression("branch=latest").get();

        // Assertions
        assertNotNull(versionMetaData);
        assertEquals(version, versionMetaData.getVersion());
        assertEquals(name, versionMetaData.getName());
        assertEquals(description, versionMetaData.getDescription());

        assertNotNull(amd);
        assertEquals(version, amd.getVersion());
        assertEquals(name, amd.getName());
        assertEquals(description, amd.getDescription());

        assertMultilineTextEquals(UPDATED_OPENAPI_YAML_CONTENT,
                IoUtil.toString(clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                        .versions().byVersionExpression("branch=latest").content().get()));
    }

    @Test
    public void testAsyncCRUD() throws Exception {
        auditLogService.resetAuditLogs();
        // Preparation
        final String groupId = "testAsyncCRUD";
        String artifactId = generateArtifactId();

        // Execution
        try {
            CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON,
                    ARTIFACT_CONTENT, ContentTypes.APPLICATION_JSON);
            VersionMetaData amd = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact)
                    .getVersion();
            Assertions.assertNotNull(amd);

            Thread.sleep(2000);

            EditableArtifactMetaData emd = new EditableArtifactMetaData();
            emd.setName("testAsyncCRUD");
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(emd);

            // Assertions
            {
                ArtifactMetaData artifactMetaData = clientV3.groups().byGroupId(groupId).artifacts()
                        .byArtifactId(artifactId).get();
                Assertions.assertNotNull(artifactMetaData);
                Assertions.assertEquals("testAsyncCRUD", artifactMetaData.getName());
            }

            CreateVersion createVersion = TestUtils.clientCreateVersion(UPDATED_CONTENT,
                    ContentTypes.APPLICATION_JSON);
            // Execution
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                    .post(createVersion);

            // Assertions
            assertEquals(UPDATED_CONTENT,
                    IoUtil.toString(clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                            .versions().byVersionExpression("branch=latest").content().get()));

            List<Map<String, String>> auditLogs = auditLogService.getAuditLogs();
            assertFalse(auditLogs.isEmpty());
            assertEquals(3, auditLogs.size()); // Expected size 3 since we performed 3 audited operations

        } finally {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete();
        }
    }

    @Test
    public void testSmoke() throws Exception {
        // Preparation
        final String groupId = "testSmoke";
        final String artifactId1 = generateArtifactId();
        final String artifactId2 = generateArtifactId();

        createArtifact(groupId, artifactId1);
        createArtifact(groupId, artifactId2);

        // Execution
        final ArtifactSearchResults searchResults = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 2;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.order = SortOrder.Asc;
        });

        // Assertions
        assertNotNull(clientV3.toString());
        assertEquals(clientV3.hashCode(), clientV3.hashCode());
        assertEquals(2, searchResults.getCount());

        // Preparation
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId1).delete();
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId2).delete();

        {
            // Execution
            final ArtifactSearchResults deletedResults = clientV3.search().artifacts().get(config -> {
                config.queryParameters.groupId = groupId;
                config.queryParameters.offset = 0;
                config.queryParameters.limit = 2;
                config.queryParameters.orderby = ArtifactSortBy.Name;
                config.queryParameters.order = SortOrder.Asc;
            });
            // Assertion
            assertEquals(0, deletedResults.getCount());
        }
    }

    @Test
    void testSearchArtifact() throws Exception {
        // PReparation
        final String groupId = "testSearchArtifact";
        clientV3.groups().byGroupId(groupId).artifacts().get();

        String artifactId = UUID.randomUUID().toString();
        String name = "n" + ThreadLocalRandom.current().nextInt(1000000);
        String artifactData = "{\"type\":\"record\",\"title\":\"" + name
                + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON,
                artifactData, ContentTypes.APPLICATION_JSON);
        createArtifact.setName(name);
        createArtifact.getFirstVersion().setName(name);
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        // Execution
        ArtifactSearchResults results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.name = name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.order = SortOrder.Asc;
        });

        // Assertions
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals(1, results.getArtifacts().size());
        Assertions.assertEquals(name, results.getArtifacts().get(0).getName());

        // Try searching for *everything*. This test was added due to Issue #661
        results = clientV3.search().artifacts().get();
        Assertions.assertNotNull(results);
        Assertions.assertTrue(results.getCount() > 0);
    }

    @Test
    void testSearchArtifactSortByCreatedOn() throws Exception {
        // Preparation
        final String groupId = "testSearchArtifactSortByCreatedOn";
        clientV3.groups().byGroupId(groupId).artifacts().get();

        String artifactId = UUID.randomUUID().toString();
        String name = "n" + ThreadLocalRandom.current().nextInt(1000000);
        String data = ("{\"type\":\"record\",\"title\":\"" + name
                + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}");

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON, data,
                ContentTypes.APPLICATION_JSON);
        createArtifact.setName(name);
        createArtifact.getFirstVersion().setName(name);

        ArtifactContent content = new ArtifactContent();
        content.setContent(data);
        VersionMetaData amd = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact)
                .getVersion();
        LOGGER.info("created " + amd.getArtifactId() + " - " + amd.getCreatedOn());

        Thread.sleep(1500);

        String artifactId2 = UUID.randomUUID().toString();
        CreateArtifact createArtifact2 = TestUtils.clientCreateArtifact(artifactId2, ArtifactType.JSON, data,
                ContentTypes.APPLICATION_JSON);
        createArtifact2.setName(name);
        VersionMetaData amd2 = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact2)
                .getVersion();
        LOGGER.info("created " + amd2.getArtifactId() + " - " + amd2.getCreatedOn());

        // Execution
        ArtifactSearchResults results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.name = name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.orderby = ArtifactSortBy.CreatedOn;
            config.queryParameters.order = SortOrder.Asc;
        });

        // Assertions
        Assertions.assertNotNull(results);
        Assertions.assertEquals(2, results.getCount());
        Assertions.assertEquals(2, results.getArtifacts().size());
        // Assertions.assertEquals(name, results.getArtifacts().get(0).getName());

        LOGGER.info("search");
        LOGGER.info(results.getArtifacts().get(0).getArtifactId() + " - "
                + results.getArtifacts().get(0).getCreatedOn());
        LOGGER.info(results.getArtifacts().get(1).getArtifactId() + " - "
                + results.getArtifacts().get(1).getCreatedOn());

        Assertions.assertEquals(artifactId, results.getArtifacts().get(0).getArtifactId());

        // Try searching for *everything*. This test was added due to Issue #661
        results = clientV3.search().artifacts().get();
        Assertions.assertNotNull(results);
        Assertions.assertTrue(results.getCount() > 0);
    }

    @Test
    void testSearchArtifactByIds() throws Exception {
        // PReparation
        final String groupId = "testSearchArtifactByIds";
        clientV3.groups().byGroupId(groupId).artifacts().get();

        String artifactId = UUID.randomUUID().toString();
        String name = "n" + ThreadLocalRandom.current().nextInt(1000000);
        String data = "{\"type\":\"record\",\"title\":\"" + name
                + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON, data,
                ContentTypes.APPLICATION_JSON);
        VersionMetaData amd = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact)
                .getVersion();
        LOGGER.info("created " + amd.getArtifactId() + " - " + amd.getCreatedOn());

        Thread.sleep(1500);

        String artifactId2 = UUID.randomUUID().toString();
        CreateArtifact createArtifact2 = TestUtils.clientCreateArtifact(artifactId2, ArtifactType.JSON, data,
                ContentTypes.APPLICATION_JSON);
        VersionMetaData amd2 = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact2)
                .getVersion();
        LOGGER.info("created " + amd2.getArtifactId() + " - " + amd2.getCreatedOn());

        ArtifactSearchResults results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.globalId = amd.getGlobalId();
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.order = SortOrder.Asc;
        });

        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals(1, results.getArtifacts().size());

        Assertions.assertEquals(artifactId, results.getArtifacts().get(0).getArtifactId());

        ArtifactSearchResults resultsByContentId = clientV3.search().artifacts().get(config -> {
            config.queryParameters.contentId = amd.getContentId();
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.order = SortOrder.Asc;
        });

        Assertions.assertNotNull(resultsByContentId);
        Assertions.assertEquals(2, resultsByContentId.getCount());
        Assertions.assertEquals(2, resultsByContentId.getArtifacts().size());

        Assertions.assertEquals(2,
                resultsByContentId.getArtifacts().stream()
                        .filter(sa -> sa.getArtifactId().equals(amd.getArtifactId())
                                || sa.getArtifactId().equals(amd2.getArtifactId()))
                        .count());
    }

    @Test
    void testSearchVersion() throws Exception {
        // Preparation
        final String groupId = "testSearchVersion";
        clientV3.groups().byGroupId(groupId).artifacts().get();

        String artifactId = UUID.randomUUID().toString();
        String name = "n" + ThreadLocalRandom.current().nextInt(1000000);
        String artifactData = "{\"type\":\"record\",\"title\":\"" + name
                + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON,
                artifactData, ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setName(name);
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        CreateVersion createVersion = TestUtils.clientCreateVersion(artifactData,
                ContentTypes.APPLICATION_JSON);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(createVersion);

        // Execution
        VersionSearchResults results = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().get(config -> {
                    config.queryParameters.offset = 0;
                    config.queryParameters.limit = 2;
                });

        // Assertions
        Assertions.assertNotNull(results);
        Assertions.assertEquals(2, results.getCount());
        Assertions.assertEquals(2, results.getVersions().size());
        Assertions.assertEquals(name, results.getVersions().get(0).getName());
    }

    @Test
    void testSearchDisabledArtifacts() throws Exception {
        // Preparation
        final String groupId = "testSearchDisabledArtifacts";
        clientV3.groups().byGroupId(groupId).artifacts().get();
        String root = "testSearchDisabledArtifact" + ThreadLocalRandom.current().nextInt(1000000);
        List<String> artifactIds = new ArrayList<>();
        List<String> versions = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            String artifactId = root + UUID.randomUUID().toString();
            String name = root + i;
            String artifactData = "{\"type\":\"record\",\"title\":\"" + name
                    + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

            CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON,
                    artifactData, ContentTypes.APPLICATION_JSON);
            VersionMetaData md = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact)
                    .getVersion();

            artifactIds.add(artifactId);
            versions.add(md.getVersion());
        }

        // Execution
        ArtifactSearchResults results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.name = root;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.order = SortOrder.Asc;
        });

        // clientV2.searchArtifacts(null, root, null, null, null, ArtifactSortBy.name, SortOrder.asc, 0, 10);

        // Assertions
        Assertions.assertNotNull(results);
        Assertions.assertEquals(5, results.getCount());
        Assertions.assertEquals(5, results.getArtifacts().size());
        Assertions.assertTrue(results.getArtifacts().stream().map(SearchedArtifact::getArtifactId)
                .collect(Collectors.toList()).containsAll(artifactIds));

        // Preparation
        // Put 2 of the 5 artifacts in DISABLED state
        WrappedVersionState newState = new WrappedVersionState();
        newState.setState(VersionState.DISABLED);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactIds.get(0)).versions()
                .byVersionExpression("1").state().put(newState);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactIds.get(3)).versions()
                .byVersionExpression("1").state().put(newState);

        // Execution
        // Check the search results still include the DISABLED artifacts
        results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.name = root;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.order = SortOrder.Asc;
        });

        // Assertions
        Assertions.assertNotNull(results);
        Assertions.assertEquals(5, results.getCount());
        Assertions.assertEquals(5, results.getArtifacts().size());
        Assertions.assertTrue(results.getArtifacts().stream().map(SearchedArtifact::getArtifactId)
                .collect(Collectors.toList()).containsAll(artifactIds));
    }

    @Test
    void testSearchDisabledVersions() throws Exception {
        // Preparation
        final String groupId = "testSearchDisabledVersions";
        clientV3.groups().byGroupId(groupId).artifacts().get();

        String artifactId = UUID.randomUUID().toString();
        String name = "testSearchDisabledVersions" + ThreadLocalRandom.current().nextInt(1000000);
        String artifactData = "{\"type\":\"record\",\"title\":\"" + name
                + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON,
                artifactData, ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setName(name);
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        CreateVersion createVersion = TestUtils.clientCreateVersion(artifactData,
                ContentTypes.APPLICATION_JSON);
        createVersion.setName(name);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(createVersion);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(createVersion);

        // Execution
        VersionSearchResults results = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().get(config -> {
                    config.queryParameters.offset = 0;
                    config.queryParameters.limit = 5;
                });

        // Assertions
        Assertions.assertNotNull(results);
        Assertions.assertEquals(3, results.getCount());
        Assertions.assertEquals(3, results.getVersions().size());
        Assertions.assertTrue(results.getVersions().stream()
                .allMatch(searchedVersion -> name.equals(searchedVersion.getName())
                        && VersionState.ENABLED.equals(searchedVersion.getState())));

        // Preparation
        // Put 2 of the 3 versions in DISABLED state
        WrappedVersionState newState = new WrappedVersionState();
        newState.setState(VersionState.DISABLED);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression("1").state().put(newState);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression("3").state().put(newState);

        // Execution
        // Check that the search results still include the DISABLED versions
        results = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .get(config -> {
                    config.queryParameters.offset = 0;
                    config.queryParameters.limit = 5;
                });

        // Assertions
        Assertions.assertNotNull(results);
        Assertions.assertEquals(3, results.getCount());
        Assertions.assertEquals(3, results.getVersions().size());
        Assertions.assertTrue(results.getVersions().stream()
                .allMatch(searchedVersion -> name.equals(searchedVersion.getName())));
        Assertions.assertEquals(2, results.getVersions().stream()
                .filter(searchedVersion -> VersionState.DISABLED.equals(searchedVersion.getState())).count());
        Assertions.assertEquals(1, results.getVersions().stream()
                .filter(searchedVersion -> VersionState.ENABLED.equals(searchedVersion.getState())).count());
    }

    @Test
    public void testLabels() throws Exception {
        // Preparation
        final String groupId = "testLabels";
        String artifactId = generateArtifactId();
        try {

            CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON,
                    "{\"name\":\"redhat\"}", ContentTypes.APPLICATION_JSON);
            clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

            EditableArtifactMetaData emd = new EditableArtifactMetaData();
            emd.setName("testProperties");

            final Map<String, Object> artifactLabels = new HashMap<>();
            artifactLabels.put("extraProperty1", "value for extra property 1");
            artifactLabels.put("extraProperty2", "value for extra property 2");
            artifactLabels.put("extraProperty3", "value for extra property 3");
            var labels = new Labels();
            labels.setAdditionalData(artifactLabels);
            emd.setLabels(labels);

            // Execution
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(emd);

            // Assertions
            {
                ArtifactMetaData artifactMetaData = clientV3.groups().byGroupId(groupId).artifacts()
                        .byArtifactId(artifactId).get();
                Assertions.assertNotNull(artifactMetaData);
                Assertions.assertEquals("testProperties", artifactMetaData.getName());
                Assertions.assertEquals(3, artifactMetaData.getLabels().getAdditionalData().size());
                Assertions.assertTrue(artifactMetaData.getLabels().getAdditionalData().keySet()
                        .containsAll(artifactLabels.keySet()));
                for (String key : artifactMetaData.getLabels().getAdditionalData().keySet()) {
                    assertEquals(artifactMetaData.getLabels().getAdditionalData().get(key),
                            artifactLabels.get(key));
                }
            }
        } finally {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete();
        }
    }

    @Test
    void nameOrderingTest() throws Exception {
        // Preparation
        final String groupId = "nameOrderingTest";
        final String firstArtifactId = generateArtifactId();
        final String secondArtifactId = generateArtifactId();
        final String thirdArtifactId = "cccTestorder";

        try {
            clientV3.groups().byGroupId(groupId).artifacts().get();

            // Create artifact 1
            String firstName = "aaaTestorder" + ThreadLocalRandom.current().nextInt(1000000);
            String artifactData = "{\"type\":\"record\",\"title\":\"" + firstName
                    + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
            CreateArtifact createArtifact1 = TestUtils.clientCreateArtifact(firstArtifactId,
                    ArtifactType.JSON, artifactData, ContentTypes.APPLICATION_JSON);
            createArtifact1.setName(firstName);
            clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact1);

            // Create artifact 2
            String secondName = "bbbTestorder" + ThreadLocalRandom.current().nextInt(1000000);
            String secondData = "{\"type\":\"record\",\"title\":\"" + secondName
                    + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
            CreateArtifact createArtifact2 = TestUtils.clientCreateArtifact(secondArtifactId,
                    ArtifactType.JSON, secondData, ContentTypes.APPLICATION_JSON);
            createArtifact2.setName(secondName);
            clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact2);

            // Create artifact 3
            String thirdData = "{\"openapi\":\"3.0.2\",\"info\":{\"description\":\"testorder\"}}";
            CreateArtifact createArtifact3 = TestUtils.clientCreateArtifact(thirdArtifactId,
                    ArtifactType.JSON, thirdData, ContentTypes.APPLICATION_JSON);
            createArtifact3.setDescription("testorder");
            clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact3);

            {
                ArtifactMetaData artifactMetaData = clientV3.groups().byGroupId(groupId).artifacts()
                        .byArtifactId(thirdArtifactId).get();
                Assertions.assertNotNull(artifactMetaData);
                Assertions.assertEquals("testorder", artifactMetaData.getDescription());
            }

            // Execution
            ArtifactSearchResults ascResults = clientV3.search().artifacts().get(config -> {
                config.queryParameters.offset = 0;
                config.queryParameters.limit = 10;
                config.queryParameters.name = "Testorder";
                config.queryParameters.groupId = groupId;
                config.queryParameters.orderby = ArtifactSortBy.Name;
                config.queryParameters.order = SortOrder.Asc;
            });

            // Assertions
            Assertions.assertNotNull(ascResults);
            Assertions.assertEquals(3, ascResults.getCount());
            Assertions.assertEquals(3, ascResults.getArtifacts().size());
            Assertions.assertEquals(firstName, ascResults.getArtifacts().get(0).getName());
            Assertions.assertEquals(secondName, ascResults.getArtifacts().get(1).getName());
            Assertions.assertNull(ascResults.getArtifacts().get(2).getName());

            // Execution
            ArtifactSearchResults descResults = clientV3.search().artifacts().get(config -> {
                config.queryParameters.offset = 0;
                config.queryParameters.limit = 10;
                config.queryParameters.name = "Testorder";
                config.queryParameters.groupId = groupId;
                config.queryParameters.orderby = ArtifactSortBy.Name;
                config.queryParameters.order = SortOrder.Desc;
            });

            // Assertions
            Assertions.assertNotNull(descResults);
            Assertions.assertEquals(3, descResults.getCount());
            Assertions.assertEquals(3, descResults.getArtifacts().size());
            Assertions.assertNull(descResults.getArtifacts().get(0).getName());
            Assertions.assertEquals(secondName, descResults.getArtifacts().get(1).getName());
            Assertions.assertEquals(firstName, descResults.getArtifacts().get(2).getName());

        } finally {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(firstArtifactId).delete();
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(secondArtifactId).delete();
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(thirdArtifactId).delete();
        }
    }

    @Test
    public void getLatestArtifact() throws Exception {
        // Preparation
        final String groupId = "getLatestArtifact";
        final String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId);

        // Execution
        InputStream amd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression("branch=latest").content().get();

        // Assertions
        assertNotNull(amd);
        assertEquals(ARTIFACT_CONTENT, IoUtil.toString(amd));
    }

    @Test
    public void getContentById() throws Exception {
        // Preparation
        final String groupId = "getContentById";
        final String artifactId = generateArtifactId();

        VersionMetaData amd = createArtifact(groupId, artifactId);
        assertNotNull(amd.getContentId());

        // Execution
        InputStream content = clientV3.ids().contentIds().byContentId(amd.getContentId()).get();

        // Assertions
        assertNotNull(content);
        assertEquals(ARTIFACT_CONTENT, IOUtils.toString(content, StandardCharsets.UTF_8));
    }

    @Test
    public void testArtifactNotFound() {
        var exception = Assertions.assertThrows(io.apicurio.registry.rest.client.models.ProblemDetails.class,
                () -> clientV3.groups().byGroupId(UUID.randomUUID().toString()).artifacts()
                        .byArtifactId(UUID.randomUUID().toString()).get());
        Assertions.assertEquals(404, exception.getStatus());
        Assertions.assertEquals("ArtifactNotFoundException", exception.getName());
    }

    @Test
    public void getContentByHash() throws Exception {
        // Preparation
        final String groupId = "getContentByHash";
        final String artifactId = generateArtifactId();

        String contentHash = DigestUtils.sha256Hex(ARTIFACT_CONTENT);

        createArtifact(groupId, artifactId);

        // Execution
        InputStream content = clientV3.ids().contentHashes().byContentHash(contentHash).get();
        assertNotNull(content);

        // Assertions
        String artifactContent = IOUtils.toString(content, StandardCharsets.UTF_8);
        assertEquals(ARTIFACT_CONTENT, artifactContent);

        // Create a second artifact using the same content but with a reference, the hash must be different
        // but it should work.
        var secondArtifactId = generateArtifactId();
        var artifactReference = new ArtifactReference();

        artifactReference.setName("testReference");
        artifactReference.setArtifactId(artifactId);
        artifactReference.setGroupId(groupId);
        artifactReference.setVersion("1");

        var artifactReferences = List.of(artifactReference);

        createArtifactWithReferences(groupId, secondArtifactId, artifactReferences);

        String referencesSerialized = RegistryContentUtils
                .serializeReferences(toReferenceDtos(artifactReferences.stream().map(r -> {
                    var ref = new io.apicurio.registry.rest.v3.beans.ArtifactReference();
                    ref.setArtifactId(r.getArtifactId());
                    ref.setGroupId(r.getGroupId());
                    ref.setName(r.getName());
                    ref.setVersion(r.getVersion());
                    return ref;
                }).collect(Collectors.toList())));

        contentHash = DigestUtils.sha256Hex(concatContentAndReferences(
                ARTIFACT_CONTENT.getBytes(StandardCharsets.UTF_8), referencesSerialized));

        // Execution
        content = clientV3.ids().contentHashes().byContentHash(contentHash).get();
        assertNotNull(content);

        // Assertions
        artifactContent = IOUtils.toString(content, StandardCharsets.UTF_8);
        assertEquals(ARTIFACT_CONTENT, artifactContent);
    }

    @Test
    public void getContentByGlobalId() throws Exception {
        // Preparation
        final String groupId = "getContentByGlobalId";
        final String artifactId = generateArtifactId();

        VersionMetaData amd = createArtifact(groupId, artifactId);

        // Execution
        {
            InputStream content = clientV3.ids().globalIds().byGlobalId(amd.getGlobalId()).get();
            assertNotNull(content);

            // Assertions
            String artifactContent = IOUtils.toString(content, StandardCharsets.UTF_8);
            assertEquals(ARTIFACT_CONTENT, artifactContent);
        }
    }

    @Test
    public void listArtifactRules() throws Exception {
        // Preparation
        final String groupId = "listArtifactRules";
        final String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId);

        final List<RuleType> emptyRules = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).rules().get();

        // Assertions
        assertNotNull(emptyRules);
        assertTrue(emptyRules.isEmpty());

        // Execution
        createArtifactRule(groupId, artifactId, io.apicurio.registry.types.RuleType.COMPATIBILITY,
                "BACKWARD");

        final List<RuleType> ruleTypes = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).rules().get();

        // Assertions
        assertNotNull(ruleTypes);
        assertFalse(ruleTypes.isEmpty());
    }

    @Test
    public void testCompatibilityWithReferences() throws Exception {
        // Preparation
        final String groupId = "testCompatibilityWithReferences";
        final String artifactId = generateArtifactId();

        // First create the references schema
        createArtifact(groupId, artifactId, ArtifactType.AVRO, REFERENCED_SCHEMA,
                ContentTypes.APPLICATION_JSON);

        io.apicurio.registry.rest.v3.beans.ArtifactReference artifactReference = new io.apicurio.registry.rest.v3.beans.ArtifactReference();
        artifactReference.setArtifactId(artifactId);
        artifactReference.setGroupId(groupId);
        artifactReference.setVersion("1");
        artifactReference.setName("com.example.common.ItemId");

        final String secondArtifactId = generateArtifactId();
        createArtifactWithReferences(groupId, secondArtifactId, ArtifactType.AVRO, SCHEMA_WITH_REFERENCE,
                ContentTypes.APPLICATION_JSON, List.of(artifactReference));

        // Create rule
        createArtifactRule(groupId, secondArtifactId, io.apicurio.registry.types.RuleType.COMPATIBILITY,
                "BACKWARD");

        createArtifactVersionExtendedRaw(groupId, secondArtifactId, SCHEMA_WITH_REFERENCE,
                ContentTypes.APPLICATION_JSON, List.of(artifactReference));

        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete();
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(secondArtifactId).delete();
    }

    @Test
    public void deleteArtifactRules() throws Exception {
        // Preparation
        final String groupId = "deleteArtifactRules";
        final String artifactId = generateArtifactId();

        prepareRuleTest(groupId, artifactId, io.apicurio.registry.types.RuleType.COMPATIBILITY, "BACKWARD");

        // Execution
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().delete();

        // Assertions
        final List<RuleType> emptyRules = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).rules().get();
        assertNotNull(emptyRules);
        assertTrue(emptyRules.isEmpty());
    }

    @Test
    public void getArtifactRuleConfig() throws Exception {
        // Preparation
        final String groupId = "getArtifactRuleConfig";
        final String artifactId = generateArtifactId();

        prepareRuleTest(groupId, artifactId, io.apicurio.registry.types.RuleType.COMPATIBILITY, "BACKWARD");

        // Execution
        final Rule rule = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules()
                .byRuleType(RuleType.COMPATIBILITY.name()).get();
        // Assertions
        assertNotNull(rule);
        assertEquals("BACKWARD", rule.getConfig());
    }

    @Test
    public void updateArtifactRuleConfig() throws Exception {
        // Preparation
        final String groupId = "updateArtifactRuleConfig";
        final String artifactId = generateArtifactId();

        prepareRuleTest(groupId, artifactId, io.apicurio.registry.types.RuleType.COMPATIBILITY, "BACKWARD");

        final Rule rule = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules()
                .byRuleType(RuleType.COMPATIBILITY.name()).get();
        assertNotNull(rule);
        assertEquals("BACKWARD", rule.getConfig());

        final Rule toUpdate = new Rule();
        toUpdate.setRuleType(RuleType.COMPATIBILITY);
        toUpdate.setConfig("FULL");

        // Execution
        final Rule updated = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules()
                .byRuleType(RuleType.COMPATIBILITY.name()).put(toUpdate);

        // Assertions
        assertNotNull(updated);
        assertEquals("FULL", updated.getConfig());
    }

    @Test
    public void testUpdateArtifact() throws Exception {

        // Preparation
        final String groupId = "testUpdateArtifact";
        final String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId);
        final String updatedContent = "{\"name\":\"ibm\"}";
        final String version = "3";
        final String name = "testUpdateArtifactName";
        final String description = "testUpdateArtifactDescription";

        // Execution
        CreateVersion createVersion = TestUtils.clientCreateVersion(updatedContent,
                ContentTypes.APPLICATION_JSON);
        createVersion.setName(name);
        createVersion.setDescription(description);
        createVersion.setVersion(version);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(createVersion);

        // Assertions
        assertEquals(updatedContent, IoUtil.toString(clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().byVersionExpression("branch=latest").content().get()));

        VersionMetaData artifactMetaData = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().byVersionExpression("branch=latest").get();
        assertNotNull(artifactMetaData);
        assertEquals(version, artifactMetaData.getVersion());
        assertEquals(name, artifactMetaData.getName());
        assertEquals(description, artifactMetaData.getDescription());
    }

    @Test
    public void testUpdateYamlArtifact() throws Exception {
        // Preparation
        final String groupId = "testUpdateYamlArtifact";
        final String artifactId = generateArtifactId();

        createOpenAPIArtifact(groupId, artifactId); // Create first version of the openapi artifact using json
        final String version = "3";
        final String name = "testUpdateYamlArtifactName";
        final String description = "testUpdateYamlArtifactDescription";

        // Execution
        CreateVersion createVersion = TestUtils.clientCreateVersion(UPDATED_OPENAPI_YAML_CONTENT,
                ContentTypes.APPLICATION_YAML);
        createVersion.setName(name);
        createVersion.setDescription(description);
        createVersion.setVersion(version);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(createVersion);

        // Assertions
        assertMultilineTextEquals(UPDATED_OPENAPI_YAML_CONTENT,
                IoUtil.toString(clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                        .versions().byVersionExpression("branch=latest").content().get()));

        VersionMetaData vmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression("branch=latest").get();
        assertNotNull(vmd);
        assertEquals(version, vmd.getVersion());
        assertEquals(name, vmd.getName());
        assertEquals(description, vmd.getDescription());
    }

    @Test
    public void deleteArtifactsInGroup() throws Exception {
        // Preparation
        final String groupId = "deleteArtifactsInGroup";
        final String firstArtifactId = generateArtifactId();
        final String secondArtifactId = generateArtifactId();
        createArtifact(groupId, firstArtifactId);
        createArtifact(groupId, secondArtifactId);

        final ArtifactSearchResults searchResults = clientV3.groups().byGroupId(groupId).artifacts().get();
        assertFalse(searchResults.getArtifacts().isEmpty());
        assertEquals(2, (int) searchResults.getCount());

        // Execution
        clientV3.groups().byGroupId(groupId).artifacts().delete();

        final ArtifactSearchResults deleted = clientV3.groups().byGroupId(groupId).artifacts().get();

        // Assertions
        assertTrue(deleted.getArtifacts().isEmpty());
        assertEquals(0, (int) deleted.getCount());
    }

    @Test
    public void searchArtifactsByContent() throws Exception {
        // Preparation
        final String groupId = "searchArtifactsByContent";
        final String firstArtifactId = generateArtifactId();
        final String secondArtifactId = generateArtifactId();

        String content = "{\"name\":\"" + TestUtils.generateSubject() + "\"}";
        createArtifact(groupId, firstArtifactId, ArtifactType.AVRO, content, ContentTypes.APPLICATION_JSON);
        createArtifact(groupId, secondArtifactId, ArtifactType.AVRO, content, ContentTypes.APPLICATION_JSON);

        // Execution
        final ArtifactSearchResults searchResults = clientV3.search().artifacts()
                .post(IoUtil.toStream(content), "application/create.extended+json");

        // Assertions
        assertEquals(2, searchResults.getCount());
    }

    @Test
    public void smokeGlobalRules() throws Exception {
        createGlobalRule(io.apicurio.registry.types.RuleType.COMPATIBILITY, "BACKWARD");
        createGlobalRule(io.apicurio.registry.types.RuleType.VALIDITY, "FORWARD");

        final List<RuleType> globalRules = clientV3.admin().rules().get();
        assertEquals(2, globalRules.size());
        assertTrue(globalRules.contains(RuleType.COMPATIBILITY));
        assertTrue(globalRules.contains(RuleType.VALIDITY));
        clientV3.admin().rules().delete();

        final List<RuleType> updatedRules = clientV3.admin().rules().get();
        assertEquals(0, updatedRules.size());
    }

    @Test
    public void getGlobalRuleConfig() throws Exception {
        // Preparation
        createGlobalRule(io.apicurio.registry.types.RuleType.COMPATIBILITY, "BACKWARD");

        // Execution
        final Rule globalRuleConfig = clientV3.admin().rules().byRuleType(RuleType.COMPATIBILITY.name())
                .get();
        // Assertions
        assertEquals(globalRuleConfig.getConfig(), "BACKWARD");
    }

    @Test
    public void updateGlobalRuleConfig() throws Exception {
        // Preparation
        createGlobalRule(io.apicurio.registry.types.RuleType.COMPATIBILITY, "BACKWARD");

        final Rule globalRuleConfig = clientV3.admin().rules().byRuleType(RuleType.COMPATIBILITY.name())
                .get();
        assertEquals(globalRuleConfig.getConfig(), "BACKWARD");

        final Rule toUpdate = new Rule();
        toUpdate.setRuleType(RuleType.COMPATIBILITY);
        toUpdate.setConfig("FORWARD");

        // Execution
        final Rule updated = clientV3.admin().rules().byRuleType(RuleType.COMPATIBILITY.name()).put(toUpdate);

        // Assertions
        assertEquals(updated.getConfig(), "FORWARD");
    }

    @Test
    public void deleteGlobalRule() throws Exception {
        // Preparation
        createGlobalRule(io.apicurio.registry.types.RuleType.COMPATIBILITY, "BACKWARD");

        final Rule globalRuleConfig = clientV3.admin().rules().byRuleType(RuleType.COMPATIBILITY.name())
                .get();
        assertEquals(globalRuleConfig.getConfig(), "BACKWARD");

        // Execution
        clientV3.admin().rules().byRuleType(RuleType.COMPATIBILITY.name()).delete();

        final List<RuleType> ruleTypes = clientV3.admin().rules().get();

        // Assertions
        assertEquals(0, ruleTypes.size());
    }

    @Test
    public void testDefaultGroup() throws Exception {
        String artifactId1 = "testDefaultGroup-" + UUID.randomUUID().toString();
        createArtifact(GroupId.DEFAULT.getRawGroupIdWithDefaultString(), artifactId1);
        verifyGroupNullInMetadata(artifactId1, ARTIFACT_CONTENT);

        String artifactId2 = "testDefaultGroup-" + UUID.randomUUID().toString();
        createArtifact(GroupId.DEFAULT.getRawGroupIdWithDefaultString(), artifactId2);
        verifyGroupNullInMetadata(artifactId2, ARTIFACT_CONTENT);

        String dummyGroup = "dummy";
        String artifactId3 = "testDefaultGroup-" + UUID.randomUUID().toString();
        createArtifact(dummyGroup, artifactId3);

        ArtifactSearchResults result = clientV3.search().artifacts().get(config -> {
            config.queryParameters.limit = 100;
        });

        SearchedArtifact artifact1 = result.getArtifacts().stream()
                .filter(s -> s.getArtifactId().equals(artifactId1)).findFirst().orElseThrow();

        assertNull(artifact1.getGroupId());

        SearchedArtifact artifact2 = result.getArtifacts().stream()
                .filter(s -> s.getArtifactId().equals(artifactId2)).findFirst().orElseThrow();

        assertNull(artifact2.getGroupId());

        SearchedArtifact artifact3 = result.getArtifacts().stream()
                .filter(s -> s.getArtifactId().equals(artifactId3)).findFirst().orElseThrow();

        assertEquals(dummyGroup, artifact3.getGroupId());

    }

    private void verifyGroupNullInMetadata(String artifactId, String content) throws Exception {
        ArtifactMetaData meta = clientV3.groups().byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString())
                .artifacts().byArtifactId(artifactId).get();
        assertTrue(new GroupId(meta.getGroupId()).isDefaultGroup());

        VersionMetaData vmeta = clientV3.groups().byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString())
                .artifacts().byArtifactId(artifactId).versions().byVersionExpression("branch=latest").get();
        assertTrue(new GroupId(vmeta.getGroupId()).isDefaultGroup());

        vmeta = clientV3.groups().byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString()).artifacts()
                .byArtifactId(artifactId).versions().byVersionExpression(vmeta.getVersion()).get();
        assertTrue(new GroupId(vmeta.getGroupId()).isDefaultGroup());

        CreateVersion createVersion = TestUtils.clientCreateVersion(content, ContentTypes.APPLICATION_JSON);
        vmeta = clientV3.groups().byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString()).artifacts()
                .byArtifactId(artifactId).versions().post(createVersion);
        assertTrue(new GroupId(vmeta.getGroupId()).isDefaultGroup());

        clientV3.groups().byGroupId("default").artifacts().get().getArtifacts().stream()
                .filter(s -> s.getArtifactId().equals(artifactId))
                .forEach(s -> assertTrue(new GroupId(s.getGroupId()).isDefaultGroup()));
    }

    private VersionMetaData createArtifact(String groupId, String artifactId) throws Exception {
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON,
                ARTIFACT_CONTENT, ContentTypes.APPLICATION_JSON);
        createArtifact.setName(artifactId);
        final VersionMetaData created = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact)
                .getVersion();
        return checkArtifact(groupId, artifactId, created);
    }

    private VersionMetaData createArtifactWithReferences(String groupId, String artifactId,
            List<ArtifactReference> artifactReferences) throws Exception {
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON,
                ARTIFACT_CONTENT, ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().getContent().setReferences(artifactReferences);
        createArtifact.setName(artifactId);
        final VersionMetaData created = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact)
                .getVersion();

        return checkArtifact(groupId, artifactId, created);
    }

    private VersionMetaData checkArtifact(String groupId, String artifactId, VersionMetaData created)
            throws Exception {
        assertNotNull(created);
        if (new GroupId(groupId).isDefaultGroup()) {
            assertNull(created.getGroupId());
        } else {
            assertEquals(groupId, created.getGroupId());
        }
        assertEquals(artifactId, created.getArtifactId());

        return created;
    }

    private VersionMetaData createOpenAPIArtifact(String groupId, String artifactId)
            throws Exception {
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.OPENAPI,
                ARTIFACT_OPENAPI_JSON_CONTENT, ContentTypes.APPLICATION_JSON);
        final VersionMetaData created = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact)
                .getVersion();
        return checkArtifact(groupId, artifactId, created);
    }

    private void prepareRuleTest(String groupId, String artifactId,
            io.apicurio.registry.types.RuleType ruleType, String ruleConfig) throws Exception {
        createArtifact(groupId, artifactId);
        createArtifactRule(groupId, artifactId, ruleType, ruleConfig);
    }

    @Test
    public void testRoleMappings() throws Exception {
        // Start with no role mappings
        List<RoleMapping> roleMappings = clientV3.admin().roleMappings().get().getRoleMappings();
        Assertions.assertTrue(roleMappings.isEmpty());

        // Add
        RoleMapping mapping = new RoleMapping();
        mapping.setPrincipalId("TestUser");
        mapping.setRole(RoleType.DEVELOPER);
        clientV3.admin().roleMappings().post(mapping);

        // Verify the mapping was added.
        RoleMapping roleMapping = clientV3.admin().roleMappings().byPrincipalId("TestUser").get();
        Assertions.assertEquals("TestUser", roleMapping.getPrincipalId());
        Assertions.assertEquals(RoleType.DEVELOPER, roleMapping.getRole());

        List<RoleMapping> mappings = clientV3.admin().roleMappings().get().getRoleMappings();
        Assertions.assertEquals(1, mappings.size());
        Assertions.assertEquals("TestUser", mappings.get(0).getPrincipalId());
        Assertions.assertEquals(RoleType.DEVELOPER, mappings.get(0).getRole());

        // Try to add the rule again - should get a 409
        var exception = Assertions.assertThrows(ApiException.class, () -> {
            clientV3.admin().roleMappings().post(mapping);
        });
        Assertions.assertEquals(409, exception.getResponseStatusCode());

        // Add another mapping
        mapping.setPrincipalId("TestUser2");
        mapping.setRole(RoleType.ADMIN);
        clientV3.admin().roleMappings().post(mapping);

        // Get the list of mappings (should be 2 of them)
        mappings = clientV3.admin().roleMappings().get().getRoleMappings();
        Assertions.assertEquals(2, mappings.size());

        // Get a single mapping by principal
        RoleMapping tu2Mapping = clientV3.admin().roleMappings().byPrincipalId("TestUser2").get();
        Assertions.assertEquals("TestUser2", tu2Mapping.getPrincipalId());
        Assertions.assertEquals(RoleType.ADMIN, tu2Mapping.getRole());

        // Update a mapping
        UpdateRole updated = new UpdateRole();
        updated.setRole(RoleType.READ_ONLY);
        clientV3.admin().roleMappings().byPrincipalId("TestUser").put(updated);

        // Get a single (updated) mapping
        RoleMapping tum = clientV3.admin().roleMappings().byPrincipalId("TestUser").get();
        Assertions.assertEquals("TestUser", tum.getPrincipalId());
        Assertions.assertEquals(RoleType.READ_ONLY, tum.getRole());

        // Try to update a role mapping that doesn't exist
        var error = Assertions.assertThrows(io.apicurio.registry.rest.client.models.ProblemDetails.class,
                () -> {
                    UpdateRole updated2 = new UpdateRole();
                    updated2.setRole(RoleType.ADMIN);
                    clientV3.admin().roleMappings().byPrincipalId("UnknownPrincipal").put(updated2);
                });

        // RoleMappingNotFoundException
        Assertions.assertEquals(404, error.getStatus());
        Assertions.assertEquals("RoleMappingNotFoundException", error.getName());

        // Delete a role mapping
        clientV3.admin().roleMappings().byPrincipalId("TestUser2").delete();

        // Get the (deleted) mapping by name (should fail with a 404)
        var exception2 = Assertions.assertThrows(io.apicurio.registry.rest.client.models.ProblemDetails.class,
                () -> {
                    clientV3.admin().roleMappings().byPrincipalId("TestUser2").get();
                });
        // RoleMappingNotFoundException
        Assertions.assertEquals(404, exception2.getStatus());
        Assertions.assertEquals("RoleMappingNotFoundException", exception2.getName());

        // Get the list of mappings (should be 1 of them)
        mappings = clientV3.admin().roleMappings().get().getRoleMappings();
        Assertions.assertEquals(1, mappings.size());
        Assertions.assertEquals("TestUser", mappings.get(0).getPrincipalId());

        // Clean up
        clientV3.admin().roleMappings().byPrincipalId("TestUser").delete();
    }

    @Test
    public void testConfigProperties() throws Exception {
        String property1Name = "apicurio.ccompat.legacy-id-mode.enabled";
        String property2Name = "apicurio.rest.deletion.artifact.enabled";

        // Start with all default values
        List<ConfigurationProperty> configProperties = clientV3.admin().config().properties().get();
        Assertions.assertFalse(configProperties.isEmpty());
        Optional<ConfigurationProperty> anonymousRead = configProperties.stream()
                .filter(cp -> cp.getName().equals(property1Name)).findFirst();
        Assertions.assertTrue(anonymousRead.isPresent());
        Assertions.assertEquals("false", anonymousRead.get().getValue());
        Optional<ConfigurationProperty> obacLimit = configProperties.stream()
                .filter(cp -> cp.getName().equals(property2Name)).findFirst();
        Assertions.assertTrue(obacLimit.isPresent());
        Assertions.assertEquals("true", obacLimit.get().getValue());

        // Change value of anonymous read access
        UpdateConfigurationProperty updateProp = new UpdateConfigurationProperty();
        updateProp.setValue("true");
        clientV3.admin().config().properties().byPropertyName(property1Name).put(updateProp);

        // Verify the property was set.
        ConfigurationProperty prop = clientV3.admin().config().properties().byPropertyName(property1Name)
                .get();
        Assertions.assertEquals(property1Name, prop.getName());
        Assertions.assertEquals("true", prop.getValue());

        List<ConfigurationProperty> properties = clientV3.admin().config().properties().get();
        prop = properties.stream().filter(cp -> cp.getName().equals(property1Name)).findFirst().get();
        Assertions.assertEquals(property1Name, prop.getName());
        Assertions.assertEquals("true", prop.getValue());

        // Set another property
        updateProp.setValue("false");
        clientV3.admin().config().properties().byPropertyName(property2Name).put(updateProp);

        // Verify the property was set.
        prop = clientV3.admin().config().properties().byPropertyName(property2Name).get();
        Assertions.assertEquals(property2Name, prop.getName());
        Assertions.assertEquals("false", prop.getValue());

        properties = clientV3.admin().config().properties().get();
        prop = properties.stream().filter(cp -> cp.getName().equals(property2Name)).findFirst().get();
        Assertions.assertEquals("false", prop.getValue());

        // Reset a config property
        clientV3.admin().config().properties().byPropertyName(property2Name).delete();

        // Verify the property was reset.
        prop = clientV3.admin().config().properties().byPropertyName(property2Name).get();
        Assertions.assertEquals(property2Name, prop.getName());
        Assertions.assertEquals("true", prop.getValue());

        properties = clientV3.admin().config().properties().get();
        prop = properties.stream().filter(cp -> cp.getName().equals(property2Name)).findFirst().get();
        Assertions.assertEquals("true", prop.getValue());

        // Reset the other property
        clientV3.admin().config().properties().byPropertyName(property1Name).delete();

        // Verify the property was reset.
        prop = clientV3.admin().config().properties().byPropertyName(property1Name).get();
        Assertions.assertEquals(property1Name, prop.getName());
        Assertions.assertEquals("false", prop.getValue());

        properties = clientV3.admin().config().properties().get();
        prop = properties.stream().filter(cp -> cp.getName().equals(property1Name)).findFirst().get();
        Assertions.assertEquals(property1Name, prop.getName());
        Assertions.assertEquals("false", prop.getValue());

        // Try to set a config property that doesn't exist.
        var exception1 = Assertions.assertThrows(io.apicurio.registry.rest.client.models.ProblemDetails.class,
                () -> {
                    updateProp.setValue("foobar");
                    clientV3.admin().config().properties().byPropertyName("property-does-not-exist")
                            .put(updateProp);
                });
        // ConfigPropertyNotFoundException
        Assertions.assertEquals(404, exception1.getStatus());
        Assertions.assertEquals("ConfigPropertyNotFoundException", exception1.getName());

        // Try to set a Long property to "foobar" (should be invalid type)
        var exception2 = Assertions.assertThrows(ApiException.class, () -> {
            updateProp.setValue("foobar");
            clientV3.admin().config().properties().byPropertyName("apicurio.download.href.ttl.seconds")
                    .put(updateProp);
        });
        // InvalidPropertyValueException
        Assertions.assertEquals(400, exception2.getResponseStatusCode());
    }

    @Test
    public void testForceArtifactType() throws Exception {
        var artifactContent = resourceToString("sample.wsdl");

        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                artifactContent, ContentTypes.APPLICATION_JSON);
        /* var postReq = */clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        var meta = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();

        assertEquals(ArtifactType.AVRO, meta.getArtifactType());

        assertTrue(clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression("branch=latest").content().get().readAllBytes().length > 0);
    }

    @Test
    public void testClientRateLimitProblemDetails() {
        TooManyRequestsMock mock = new TooManyRequestsMock();
        mock.start();
        try {
            var adapter = new VertXRequestAdapter(vertx);
            adapter.setBaseUrl(mock.getMockUrl());
            io.apicurio.registry.rest.client.RegistryClient client = new io.apicurio.registry.rest.client.RegistryClient(
                    adapter);

            var execution1 = Assertions.assertThrows(ApiException.class,
                    () -> client.groups().byGroupId("test").artifacts().byArtifactId("test").get());
            Assertions.assertEquals(429, execution1.getResponseStatusCode());

            CreateArtifact createArtifact = TestUtils.clientCreateArtifact("aaa", ArtifactType.JSON, "{}",
                    ContentTypes.APPLICATION_JSON);
            var exception2 = Assertions.assertThrows(ApiException.class,
                    () -> client.groups().byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString())
                            .artifacts().post(createArtifact));
            Assertions.assertEquals(429, exception2.getResponseStatusCode());

            var exception3 = Assertions.assertThrows(ApiException.class,
                    () -> client.ids().globalIds().byGlobalId(5L).get());
            Assertions.assertEquals(429, exception3.getResponseStatusCode());
        } finally {
            mock.stop();
        }
    }
}
