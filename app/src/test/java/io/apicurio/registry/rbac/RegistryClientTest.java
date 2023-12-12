package io.apicurio.registry.rbac;

import com.microsoft.kiota.ApiException;
import com.microsoft.kiota.authentication.AnonymousAuthenticationProvider;
import com.microsoft.kiota.http.OkHttpRequestAdapter;
import io.apicurio.registry.AbstractRegistryTestBase;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.*;
import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.ApplicationRbacEnabledProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.registry.utils.tests.TooManyRequestsMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.apicurio.registry.utils.tests.TestUtils.retry;
import static org.junit.jupiter.api.Assertions.*;

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
        ArtifactContent content = new ArtifactContent();
        content.setContent(ARTIFACT_CONTENT);
        final ArtifactMetaData created = clientV3.groups().byGroupId(groupId).artifacts()
                .post(content, config -> {
                    config.queryParameters.ifExists = "FAIL";
                    config.headers.add("X-Registry-ArtifactId", artifactId);
                    config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
                    config.headers.add("X-Registry-Name", name);
                    config.headers.add("X-Registry-Description", description);
                }).get(3, TimeUnit.SECONDS);

        // Assertions
        assertNotNull(created);
        assertEquals(groupId, created.getGroupId());
        assertEquals(artifactId, created.getId());
        assertEquals(version, created.getVersion());
        assertEquals(name, created.getName());
        assertEquals(description, created.getDescription());
        assertEquals(ARTIFACT_CONTENT,
                new String(clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get()
                        .get(3, TimeUnit.SECONDS).readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    public void groupsCrud() throws Exception {
        // Preparation
        final String groupId = UUID.randomUUID().toString();
        CreateGroupMetaData groupMetaData = new CreateGroupMetaData();
        groupMetaData.setId(groupId);
        groupMetaData.setDescription("Groups test crud");
        io.apicurio.registry.rest.client.models.Properties props = new io.apicurio.registry.rest.client.models.Properties();
        props.setAdditionalData(Map.of("p1", "v1", "p2", "v2"));
        groupMetaData.setProperties(props);

        clientV3.groups().post(groupMetaData).get(3, TimeUnit.SECONDS);

        final GroupMetaData artifactGroup = clientV3.groups().byGroupId(groupId).get().get(3,
                TimeUnit.SECONDS);
        assertEquals(groupMetaData.getId(), artifactGroup.getId());
        assertEquals(groupMetaData.getDescription(), artifactGroup.getDescription());
        assertEquals(groupMetaData.getProperties().getAdditionalData(),
                artifactGroup.getProperties().getAdditionalData());

        String group1Id = UUID.randomUUID().toString();
        String group2Id = UUID.randomUUID().toString();
        String group3Id = UUID.randomUUID().toString();

        groupMetaData.setId(group1Id);
        clientV3.groups().post(groupMetaData).get(3, TimeUnit.SECONDS);
        groupMetaData.setId(group2Id);
        clientV3.groups().post(groupMetaData).get(3, TimeUnit.SECONDS);
        groupMetaData.setId(group3Id);
        clientV3.groups().post(groupMetaData).get(3, TimeUnit.SECONDS);

        GroupSearchResults groupSearchResults = clientV3.groups().get(config -> {
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 100;
            config.queryParameters.order = "asc";
            config.queryParameters.orderby = "name";
        }).get(3, TimeUnit.SECONDS);
        assertTrue(groupSearchResults.getCount() >= 4);

        final List<String> groupIds = groupSearchResults.getGroups().stream().map(SearchedGroup::getId)
                .collect(Collectors.toList());

        assertTrue(groupIds.containsAll(List.of(groupId, group1Id, group2Id, group3Id)));
        clientV3.groups().byGroupId(groupId).delete().get(3, TimeUnit.SECONDS);

        var executionException = Assert.assertThrows(ExecutionException.class,
                () -> clientV3.groups().byGroupId(groupId).get().get(3, TimeUnit.SECONDS));
        Assertions.assertNotNull(executionException.getCause());
        Assertions.assertEquals(io.apicurio.registry.rest.client.models.Error.class,
                executionException.getCause().getClass());
        Assertions.assertEquals("GroupNotFoundException",
                ((io.apicurio.registry.rest.client.models.Error) executionException.getCause()).getName());
        Assertions.assertEquals(404,
                ((io.apicurio.registry.rest.client.models.Error) executionException.getCause())
                        .getErrorCode());
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
        ArtifactContent content = new ArtifactContent();
        content.setContent(ARTIFACT_OPENAPI_YAML_CONTENT);
        final ArtifactMetaData created = clientV3.groups().byGroupId(groupId).artifacts()
                .post(content, config -> {
                    config.queryParameters.canonical = false;
                    config.queryParameters.ifExists = "FAIL";
                    config.headers.add("X-Registry-ArtifactId", artifactId);
                    config.headers.add("X-Registry-ArtifactType", ArtifactType.OPENAPI);
                    config.headers.add("X-Registry-Name", name);
                    config.headers.add("X-Registry-Description", description);
                    config.headers.add("X-Registry-Version", version);
                    config.headers.add("Content-Type", "application/create.extended+json");
                }).get(3, TimeUnit.SECONDS);

        // Assertions
        assertNotNull(created);
        assertEquals(groupId, created.getGroupId());
        assertEquals(artifactId, created.getId());
        assertEquals(version, created.getVersion());
        assertEquals(name, created.getName());
        assertEquals(description, created.getDescription());
        assertMultilineTextEquals(ARTIFACT_OPENAPI_JSON_CONTENT, IoUtil.toString(clientV3.groups()
                .byGroupId(groupId).artifacts().byArtifactId(artifactId).get().get(3, TimeUnit.SECONDS)));
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
        ArtifactContent content = new ArtifactContent();
        content.setContent(UPDATED_CONTENT);
        VersionMetaData versionMetaData = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().post(content, config -> {
                    config.headers.add("X-Registry-Name", name);
                    config.headers.add("X-Registry-Description", description);
                    config.headers.add("X-Registry-Version", version);
                    config.headers.add("X-Registry-ArtifactType", ArtifactType.OPENAPI);
                    config.headers.add("Content-Type", "application/create.extended+json");
                }).get(3, TimeUnit.SECONDS);

        ArtifactMetaData amd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .meta().get().get(3, TimeUnit.SECONDS);

        // Assertions
        assertNotNull(versionMetaData);
        assertEquals(version, versionMetaData.getVersion());
        assertEquals(name, versionMetaData.getName());
        assertEquals(description, versionMetaData.getDescription());

        assertNotNull(amd);
        assertEquals(version, amd.getVersion());
        assertEquals(name, amd.getName());
        assertEquals(description, amd.getDescription());

        assertEquals(UPDATED_CONTENT, IoUtil.toString(clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).get().get(3, TimeUnit.SECONDS)));
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
        ArtifactContent content = new ArtifactContent();
        content.setContent(UPDATED_OPENAPI_YAML_CONTENT);
        var postReq = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .toPostRequestInformation(content, config -> {
                    config.headers.add("X-Registry-Name", name);
                    config.headers.add("X-Registry-Description", description);
                    config.headers.add("X-Registry-Version", version);
                    config.headers.add("X-Registry-ArtifactType", ArtifactType.OPENAPI);
                });
        // HACK to set the correct body content
        postReq.setStreamContent(new ByteArrayInputStream(UPDATED_OPENAPI_YAML_CONTENT.getBytes()));
        // HACK to set the correct header
        postReq.headers.replace("Content-Type", Set.of(ContentTypes.APPLICATION_YAML));
        VersionMetaData versionMetaData = anonymousAdapter
                .sendAsync(postReq, VersionMetaData::createFromDiscriminatorValue, new HashMap<>())
                .get(3, TimeUnit.SECONDS);

        ArtifactMetaData amd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .meta().get().get(3, TimeUnit.SECONDS);

        // Assertions
        assertNotNull(versionMetaData);
        assertEquals(version, versionMetaData.getVersion());
        assertEquals(name, versionMetaData.getName());
        assertEquals(description, versionMetaData.getDescription());

        assertNotNull(amd);
        assertEquals(version, amd.getVersion());
        assertEquals(name, amd.getName());
        assertEquals(description, amd.getDescription());

        assertMultilineTextEquals(UPDATED_OPENAPI_JSON_CONTENT, IoUtil.toString(clientV3.groups()
                .byGroupId(groupId).artifacts().byArtifactId(artifactId).get().get(3, TimeUnit.SECONDS)));
    }

    @Test
    public void testAsyncCRUD() throws Exception {
        auditLogService.resetAuditLogs();
        // Preparation
        final String groupId = "testAsyncCRUD";
        String artifactId = generateArtifactId();

        // Execution
        try {
            ArtifactContent content = new ArtifactContent();
            content.setContent(ARTIFACT_CONTENT);
            ArtifactMetaData amd = clientV3.groups().byGroupId(groupId).artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
                config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
                config.headers.add("Content-Type", "application/create.extended+json");
            }).get(3, TimeUnit.SECONDS);
            Assertions.assertNotNull(amd);

            Thread.sleep(2000);

            EditableMetaData emd = new EditableMetaData();
            emd.setName("testAsyncCRUD");
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().put(emd).get(3,
                    TimeUnit.SECONDS);

            // Assertions
            retry(() -> {
                ArtifactMetaData artifactMetaData = clientV3.groups().byGroupId(groupId).artifacts()
                        .byArtifactId(artifactId).meta().get().get(3, TimeUnit.SECONDS);
                Assertions.assertNotNull(artifactMetaData);
                Assertions.assertEquals("testAsyncCRUD", artifactMetaData.getName());
            });

            content.setContent(UPDATED_CONTENT);

            // Execution
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(content).get(3,
                    TimeUnit.SECONDS);

            // Assertions
            assertEquals(UPDATED_CONTENT, IoUtil.toString(clientV3.groups().byGroupId(groupId).artifacts()
                    .byArtifactId(artifactId).get().get(3, TimeUnit.SECONDS)));

            List<Map<String, String>> auditLogs = auditLogService.getAuditLogs();
            assertFalse(auditLogs.isEmpty());
            assertEquals(3, auditLogs.size()); // Expected size 3 since we performed 3 audited operations

        } finally {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete().get(3,
                    TimeUnit.SECONDS);
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
            config.queryParameters.group = groupId;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 2;
            config.queryParameters.orderby = "name";
            config.queryParameters.order = "asc";
        }).get(3, TimeUnit.SECONDS);

        // Assertions
        assertNotNull(clientV3.toString());
        assertEquals(clientV3.hashCode(), clientV3.hashCode());
        assertEquals(2, searchResults.getCount());

        // Preparation
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId1).delete().get(3,
                TimeUnit.SECONDS);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId2).delete().get(3,
                TimeUnit.SECONDS);

        TestUtils.retry(() -> {
            // Execution
            final ArtifactSearchResults deletedResults = clientV3.search().artifacts().get(config -> {
                config.queryParameters.group = groupId;
                config.queryParameters.offset = 0;
                config.queryParameters.limit = 2;
                config.queryParameters.orderby = "name";
                config.queryParameters.order = "asc";
            }).get(3, TimeUnit.SECONDS);
            // Assertion
            assertEquals(0, deletedResults.getCount());
        });
    }

    @Test
    void testSearchArtifact() throws Exception {
        // PReparation
        final String groupId = "testSearchArtifact";
        clientV3.groups().byGroupId(groupId).artifacts().get().get(3, TimeUnit.SECONDS);

        String artifactId = UUID.randomUUID().toString();
        String name = "n" + ThreadLocalRandom.current().nextInt(1000000);
        String artifactData = "{\"type\":\"record\",\"title\":\"" + name
                + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        ArtifactContent content = new ArtifactContent();
        content.setContent(artifactData);
        clientV3.groups().byGroupId(groupId).artifacts().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", artifactId);
            config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
            config.headers.add("Content-Type", "application/create.extended+json");
        }).get(3, TimeUnit.SECONDS);

        // Execution
        ArtifactSearchResults results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.name = name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.orderby = "name";
            config.queryParameters.order = "asc";
        }).get(3, TimeUnit.SECONDS);

        // Assertions
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals(1, results.getArtifacts().size());
        Assertions.assertEquals(name, results.getArtifacts().get(0).getName());

        // Try searching for *everything*. This test was added due to Issue #661
        results = clientV3.search().artifacts().get().get(3, TimeUnit.SECONDS);
        Assertions.assertNotNull(results);
        Assertions.assertTrue(results.getCount() > 0);
    }

    @Test
    void testSearchArtifactSortByCreatedOn() throws Exception {
        // PReparation
        final String groupId = "testSearchArtifactSortByCreatedOn";
        clientV3.groups().byGroupId(groupId).artifacts().get().get(3, TimeUnit.SECONDS);

        String artifactId = UUID.randomUUID().toString();
        String name = "n" + ThreadLocalRandom.current().nextInt(1000000);
        String data = ("{\"type\":\"record\",\"title\":\"" + name
                + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}");

        ArtifactContent content = new ArtifactContent();
        content.setContent(data);
        ArtifactMetaData amd = clientV3.groups().byGroupId(groupId).artifacts().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", artifactId);
            config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
            config.headers.add("Content-Type", "application/create.extended+json");
        }).get(3, TimeUnit.SECONDS);
        LOGGER.info("created " + amd.getId() + " - " + amd.getCreatedOn());

        Thread.sleep(1500);

        String artifactId2 = UUID.randomUUID().toString();
        ArtifactMetaData amd2 = clientV3.groups().byGroupId(groupId).artifacts().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", artifactId2);
            config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
            config.headers.add("Content-Type", "application/create.extended+json");
        }).get(3, TimeUnit.SECONDS);
        LOGGER.info("created " + amd2.getId() + " - " + amd2.getCreatedOn());

        // Execution
        ArtifactSearchResults results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.name = name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.orderby = "createdOn";
            config.queryParameters.order = "asc";
        }).get(3, TimeUnit.SECONDS);

        // Assertions
        Assertions.assertNotNull(results);
        Assertions.assertEquals(2, results.getCount());
        Assertions.assertEquals(2, results.getArtifacts().size());
        // Assertions.assertEquals(name, results.getArtifacts().get(0).getName());

        LOGGER.info("search");
        LOGGER.info(
                results.getArtifacts().get(0).getId() + " - " + results.getArtifacts().get(0).getCreatedOn());
        LOGGER.info(
                results.getArtifacts().get(1).getId() + " - " + results.getArtifacts().get(1).getCreatedOn());

        Assertions.assertEquals(artifactId, results.getArtifacts().get(0).getId());

        // Try searching for *everything*. This test was added due to Issue #661
        results = clientV3.search().artifacts().get().get(3, TimeUnit.SECONDS);
        Assertions.assertNotNull(results);
        Assertions.assertTrue(results.getCount() > 0);
    }

    @Test
    void testSearchArtifactByIds() throws Exception {
        // PReparation
        final String groupId = "testSearchArtifactByIds";
        clientV3.groups().byGroupId(groupId).artifacts().get().get(3, TimeUnit.SECONDS);

        String artifactId = UUID.randomUUID().toString();
        String name = "n" + ThreadLocalRandom.current().nextInt(1000000);
        String data = "{\"type\":\"record\",\"title\":\"" + name
                + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        ArtifactContent content = new ArtifactContent();
        content.setContent(data);
        ArtifactMetaData amd = clientV3.groups().byGroupId(groupId).artifacts().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", artifactId);
            config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
            config.headers.add("Content-Type", "application/create.extended+json");
        }).get(3, TimeUnit.SECONDS);
        LOGGER.info("created " + amd.getId() + " - " + amd.getCreatedOn());

        Thread.sleep(1500);

        String artifactId2 = UUID.randomUUID().toString();
        ArtifactMetaData amd2 = clientV3.groups().byGroupId(groupId).artifacts().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", artifactId2);
            config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
            config.headers.add("Content-Type", "application/create.extended+json");
        }).get(3, TimeUnit.SECONDS);
        LOGGER.info("created " + amd2.getId() + " - " + amd2.getCreatedOn());

        ArtifactSearchResults results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.globalId = amd.getGlobalId();
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.orderby = "name";
            config.queryParameters.order = "asc";
        }).get(3, TimeUnit.SECONDS);

        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals(1, results.getArtifacts().size());

        Assertions.assertEquals(artifactId, results.getArtifacts().get(0).getId());

        ArtifactSearchResults resultsByContentId = clientV3.search().artifacts().get(config -> {
            config.queryParameters.contentId = amd.getContentId();
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.orderby = "name";
            config.queryParameters.order = "asc";
        }).get(3, TimeUnit.SECONDS);

        Assertions.assertNotNull(resultsByContentId);
        Assertions.assertEquals(2, resultsByContentId.getCount());
        Assertions.assertEquals(2, resultsByContentId.getArtifacts().size());

        Assertions.assertEquals(2, resultsByContentId.getArtifacts().stream()
                .filter(sa -> sa.getId().equals(amd.getId()) || sa.getId().equals(amd2.getId())).count());
    }

    @Test
    void testSearchVersion() throws Exception {
        // Preparation
        final String groupId = "testSearchVersion";
        clientV3.groups().byGroupId(groupId).artifacts().get().get(3, TimeUnit.SECONDS);

        String artifactId = UUID.randomUUID().toString();
        String name = "n" + ThreadLocalRandom.current().nextInt(1000000);
        String artifactData = "{\"type\":\"record\",\"title\":\"" + name
                + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        ArtifactContent content = new ArtifactContent();
        content.setContent(artifactData);
        clientV3.groups().byGroupId(groupId).artifacts().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", artifactId);
            config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
            config.headers.add("Content-Type", "application/create.extended+json");
        }).get(3, TimeUnit.SECONDS);

        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(content, config -> {
                    config.headers.add("X-Registry-ArtifactId", artifactId);
                    config.headers.add("Content-Type", "application/create.extended+json");
                }).get(3, TimeUnit.SECONDS);

        // Execution
        VersionSearchResults results = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().get(config -> {
                    config.queryParameters.offset = 0;
                    config.queryParameters.limit = 2;
                }).get(3, TimeUnit.SECONDS);

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
        clientV3.groups().byGroupId(groupId).artifacts().get().get(3, TimeUnit.SECONDS);
        String root = "testSearchDisabledArtifact" + ThreadLocalRandom.current().nextInt(1000000);
        List<String> artifactIds = new ArrayList<>();
        List<String> versions = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            String artifactId = root + UUID.randomUUID().toString();
            String name = root + i;
            String artifactData = "{\"type\":\"record\",\"title\":\"" + name
                    + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

            ArtifactContent content = new ArtifactContent();
            content.setContent(artifactData);
            ArtifactMetaData md = clientV3.groups().byGroupId(groupId).artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
                config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
                config.headers.add("Content-Type", "application/create.extended+json");
            }).get(3, TimeUnit.SECONDS);

            artifactIds.add(artifactId);
            versions.add(md.getVersion());
        }

        // Execution
        ArtifactSearchResults results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.name = root;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.orderby = "name";
            config.queryParameters.order = "asc";
        }).get(3, TimeUnit.SECONDS);

        // clientV2.searchArtifacts(null, root, null, null, null, SortBy.name, SortOrder.asc, 0, 10);

        // Assertions
        Assertions.assertNotNull(results);
        Assertions.assertEquals(5, results.getCount());
        Assertions.assertEquals(5, results.getArtifacts().size());
        Assertions.assertTrue(results.getArtifacts().stream().map(SearchedArtifact::getId)
                .collect(Collectors.toList()).containsAll(artifactIds));

        // Preparation
        // Put 2 of the 5 artifacts in DISABLED state
        UpdateState us = new UpdateState();
        us.setState(io.apicurio.registry.rest.client.models.ArtifactState.DISABLED);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactIds.get(0)).state().put(us)
                .get(3, TimeUnit.SECONDS);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactIds.get(3)).state().put(us)
                .get(3, TimeUnit.SECONDS);

        // Execution
        // Check the search results still include the DISABLED artifacts
        results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.name = root;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.orderby = "name";
            config.queryParameters.order = "asc";
        }).get(3, TimeUnit.SECONDS);

        // Assertions
        Assertions.assertNotNull(results);
        Assertions.assertEquals(5, results.getCount());
        Assertions.assertEquals(5, results.getArtifacts().size());
        Assertions.assertTrue(results.getArtifacts().stream().map(SearchedArtifact::getId)
                .collect(Collectors.toList()).containsAll(artifactIds));
        Assertions.assertEquals(2,
                results.getArtifacts().stream().filter(
                        searchedArtifact -> io.apicurio.registry.rest.client.models.ArtifactState.DISABLED
                                .equals(searchedArtifact.getState()))
                        .count());
        Assertions.assertEquals(3,
                results.getArtifacts().stream().filter(
                        searchedArtifact -> io.apicurio.registry.rest.client.models.ArtifactState.ENABLED
                                .equals(searchedArtifact.getState()))
                        .count());
    }

    @Test
    void testSearchDisabledVersions() throws Exception {
        // Preparation
        final String groupId = "testSearchDisabledVersions";
        clientV3.groups().byGroupId(groupId).artifacts().get().get(3, TimeUnit.SECONDS);

        String artifactId = UUID.randomUUID().toString();
        String name = "testSearchDisabledVersions" + ThreadLocalRandom.current().nextInt(1000000);
        String artifactData = "{\"type\":\"record\",\"title\":\"" + name
                + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        ArtifactContent content = new ArtifactContent();
        content.setContent(artifactData);
        clientV3.groups().byGroupId(groupId).artifacts().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", artifactId);
            config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
            config.headers.add("Content-Type", "application/create.extended+json");
        }).get(3, TimeUnit.SECONDS);

        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(content, config -> {
                    config.headers.add("X-Registry-ArtifactId", artifactId);
                }).get(3, TimeUnit.SECONDS);

        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(content, config -> {
                    config.headers.add("X-Registry-ArtifactId", artifactId);
                }).get(3, TimeUnit.SECONDS);

        // Execution
        VersionSearchResults results = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().get(config -> {
                    config.queryParameters.offset = 0;
                    config.queryParameters.limit = 5;
                }).get(3, TimeUnit.SECONDS);

        // Assertions
        Assertions.assertNotNull(results);
        Assertions.assertEquals(3, results.getCount());
        Assertions.assertEquals(3, results.getVersions().size());
        Assertions.assertTrue(results.getVersions().stream()
                .allMatch(searchedVersion -> name.equals(searchedVersion.getName())
                        && ArtifactState.ENABLED.equals(searchedVersion.getState())));

        // Preparation
        // Put 2 of the 3 versions in DISABLED state
        UpdateState us = new UpdateState();
        us.setState(ArtifactState.DISABLED);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion("1")
                .state().put(us).get(3, TimeUnit.SECONDS);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion("3")
                .state().put(us).get(3, TimeUnit.SECONDS);

        // Execution
        // Check that the search results still include the DISABLED versions
        results = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .get(config -> {
                    config.queryParameters.offset = 0;
                    config.queryParameters.limit = 5;
                }).get(3, TimeUnit.SECONDS);

        // Assertions
        Assertions.assertNotNull(results);
        Assertions.assertEquals(3, results.getCount());
        Assertions.assertEquals(3, results.getVersions().size());
        Assertions.assertTrue(results.getVersions().stream()
                .allMatch(searchedVersion -> name.equals(searchedVersion.getName())));
        Assertions.assertEquals(2,
                results.getVersions().stream()
                        .filter(searchedVersion -> ArtifactState.DISABLED.equals(searchedVersion.getState()))
                        .count());
        Assertions.assertEquals(1, results.getVersions().stream()
                .filter(searchedVersion -> ArtifactState.ENABLED.equals(searchedVersion.getState())).count());
    }

    @Test
    public void testLabels() throws Exception {
        // Preparation
        final String groupId = "testLabels";
        String artifactId = generateArtifactId();

        try {
            ArtifactContent content = new ArtifactContent();
            content.setContent("{\"name\":\"redhat\"}");
            clientV3.groups().byGroupId(groupId).artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
                config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
                config.headers.add("Content-Type", "application/create.extended+json");
            }).get(3, TimeUnit.SECONDS);
            EditableMetaData emd = new EditableMetaData();
            emd.setName("testLabels");

            final List<String> artifactLabels = Arrays.asList("Open Api", "Awesome Artifact", "JSON",
                    "registry-client-test-testLabels");
            emd.setLabels(artifactLabels);

            // Execution
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().put(emd).get(3,
                    TimeUnit.SECONDS);

            // Assertions
            retry(() -> {
                ArtifactMetaData artifactMetaData = clientV3.groups().byGroupId(groupId).artifacts()
                        .byArtifactId(artifactId).meta().get().get(3, TimeUnit.SECONDS);
                Assertions.assertNotNull(artifactMetaData);
                Assertions.assertEquals("testLabels", artifactMetaData.getName());
                Assertions.assertEquals(4, artifactMetaData.getLabels().size());
                Assertions.assertTrue(artifactMetaData.getLabels().containsAll(artifactLabels));
            });

            retry((() -> {
                ArtifactSearchResults results = clientV3.search().artifacts().get(config -> {
                    config.queryParameters.offset = 0;
                    config.queryParameters.limit = 10;
                    config.queryParameters.name = "testLabels";
                    config.queryParameters.orderby = "name";
                    config.queryParameters.order = "asc";
                }).get(3, TimeUnit.SECONDS);
                Assertions.assertNotNull(results);
                Assertions.assertEquals(1, results.getCount());
                Assertions.assertEquals(1, results.getArtifacts().size());
                Assertions.assertTrue(results.getArtifacts().get(0).getLabels().containsAll(artifactLabels));
            }));
        } finally {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete().get(3,
                    TimeUnit.SECONDS);
        }
    }

    @Test
    public void testProperties() throws Exception {
        // Preparation
        final String groupId = "testProperties";
        String artifactId = generateArtifactId();
        try {
            ArtifactContent content = new ArtifactContent();
            content.setContent("{\"name\":\"redhat\"}");
            clientV3.groups().byGroupId(groupId).artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
                config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
                config.headers.add("Content-Type", "application/create.extended+json");
            }).get(3, TimeUnit.SECONDS);

            EditableMetaData emd = new EditableMetaData();
            emd.setName("testProperties");

            final Map<String, Object> artifactProperties = new HashMap<>();
            artifactProperties.put("extraProperty1", "value for extra property 1");
            artifactProperties.put("extraProperty2", "value for extra property 2");
            artifactProperties.put("extraProperty3", "value for extra property 3");
            var props = new io.apicurio.registry.rest.client.models.Properties();
            props.setAdditionalData(artifactProperties);
            emd.setProperties(props);

            // Execution
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().put(emd).get(3,
                    TimeUnit.SECONDS);

            // Assertions
            retry(() -> {
                ArtifactMetaData artifactMetaData = clientV3.groups().byGroupId(groupId).artifacts()
                        .byArtifactId(artifactId).meta().get().get(3, TimeUnit.SECONDS);
                Assertions.assertNotNull(artifactMetaData);
                Assertions.assertEquals("testProperties", artifactMetaData.getName());
                Assertions.assertEquals(3, artifactMetaData.getProperties().getAdditionalData().size());
                Assertions.assertTrue(artifactMetaData.getProperties().getAdditionalData().keySet()
                        .containsAll(artifactProperties.keySet()));
                for (String key : artifactMetaData.getProperties().getAdditionalData().keySet()) {
                    assertEquals(artifactMetaData.getProperties().getAdditionalData().get(key),
                            artifactProperties.get(key));
                }
            });
        } finally {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete().get(3,
                    TimeUnit.SECONDS);
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
            clientV3.groups().byGroupId(groupId).artifacts().get().get(3, TimeUnit.SECONDS);

            // Create artifact 1
            String firstName = "aaaTestorder" + ThreadLocalRandom.current().nextInt(1000000);
            String artifactData = "{\"type\":\"record\",\"title\":\"" + firstName
                    + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

            ArtifactContent content = new ArtifactContent();
            content.setContent(artifactData);
            clientV3.groups().byGroupId(groupId).artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", firstArtifactId);
                config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
                config.headers.add("Content-Type", "application/create.extended+json");
            }).get(3, TimeUnit.SECONDS);
            // Create artifact 2
            String secondName = "bbbTestorder" + ThreadLocalRandom.current().nextInt(1000000);
            String secondData = "{\"type\":\"record\",\"title\":\"" + secondName
                    + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

            content.setContent(secondData);
            clientV3.groups().byGroupId(groupId).artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", secondArtifactId);
                config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
                config.headers.add("Content-Type", "application/create.extended+json");
            }).get(3, TimeUnit.SECONDS);
            // Create artifact 3
            String thirdData = "{\"openapi\":\"3.0.2\",\"info\":{\"description\":\"testorder\"}}";

            content.setContent(thirdData);
            clientV3.groups().byGroupId(groupId).artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", thirdArtifactId);
                config.headers.add("X-Registry-ArtifactType", ArtifactType.OPENAPI);
                config.headers.add("Content-Type", "application/create.extended+json");
            }).get(3, TimeUnit.SECONDS);

            retry(() -> {
                ArtifactMetaData artifactMetaData = clientV3.groups().byGroupId(groupId).artifacts()
                        .byArtifactId(thirdArtifactId).meta().get().get(3, TimeUnit.SECONDS);
                Assertions.assertNotNull(artifactMetaData);
                Assertions.assertEquals("testorder", artifactMetaData.getDescription());
            });

            // Execution
            ArtifactSearchResults ascResults = clientV3.search().artifacts().get(config -> {
                config.queryParameters.offset = 0;
                config.queryParameters.limit = 10;
                config.queryParameters.name = "Testorder";
                config.queryParameters.group = groupId;
                config.queryParameters.orderby = "name";
                config.queryParameters.order = "asc";
            }).get(3, TimeUnit.SECONDS);

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
                config.queryParameters.group = groupId;
                config.queryParameters.orderby = "name";
                config.queryParameters.order = "desc";
            }).get(3, TimeUnit.SECONDS);

            // Assertions
            Assertions.assertNotNull(descResults);
            Assertions.assertEquals(3, descResults.getCount());
            Assertions.assertEquals(3, descResults.getArtifacts().size());
            Assertions.assertNull(descResults.getArtifacts().get(0).getName());
            Assertions.assertEquals(secondName, descResults.getArtifacts().get(1).getName());
            Assertions.assertEquals(firstName, descResults.getArtifacts().get(2).getName());

        } finally {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(firstArtifactId).delete().get(3,
                    TimeUnit.SECONDS);
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(secondArtifactId).delete().get(3,
                    TimeUnit.SECONDS);
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(thirdArtifactId).delete().get(3,
                    TimeUnit.SECONDS);
        }
    }

    @Test
    public void getLatestArtifact() throws Exception {
        // Preparation
        final String groupId = "getLatestArtifact";
        final String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId);

        // Execution
        InputStream amd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get()
                .get(3, TimeUnit.SECONDS);

        // Assertions
        assertNotNull(amd);
        assertEquals(ARTIFACT_CONTENT, IoUtil.toString(amd));
    }

    @Test
    public void getContentById() throws Exception {
        // Preparation
        final String groupId = "getContentById";
        final String artifactId = generateArtifactId();

        ArtifactMetaData amd = createArtifact(groupId, artifactId);
        assertNotNull(amd.getContentId());

        // Execution
        InputStream content = clientV3.ids().contentIds().byContentId(amd.getContentId()).get().get(3,
                TimeUnit.SECONDS);

        // Assertions
        assertNotNull(content);
        assertEquals(ARTIFACT_CONTENT, IOUtils.toString(content, StandardCharsets.UTF_8));
    }

    @Test
    public void testArtifactNotFound() {
        var executionException = Assertions.assertThrows(ExecutionException.class,
                () -> clientV3.groups().byGroupId(UUID.randomUUID().toString()).artifacts()
                        .byArtifactId(UUID.randomUUID().toString()).meta().get().get(3, TimeUnit.SECONDS));
        Assertions.assertNotNull(executionException.getCause());
        Assertions.assertEquals(io.apicurio.registry.rest.client.models.Error.class,
                executionException.getCause().getClass());
        Assertions.assertEquals(404,
                ((io.apicurio.registry.rest.client.models.Error) executionException.getCause())
                        .getErrorCode());
        Assertions.assertEquals("ArtifactNotFoundException",
                ((io.apicurio.registry.rest.client.models.Error) executionException.getCause()).getName());
    }

    @Test
    public void getArtifactVersionMetadataByContent() throws Exception {
        // Preparation
        final String groupId = "getArtifactVersionMetadataByContent";
        final String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId);

        // Execution
        ArtifactContent content = new ArtifactContent();
        content.setContent(ARTIFACT_CONTENT);
        final VersionMetaData versionMetaData = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).meta().post(content).get(3, TimeUnit.SECONDS);

        assertNotNull(versionMetaData);

        // Create a second artifact using the same content but with a reference, since this version has
        // references, a new artifact version must be created.
        var secondArtifactId = generateArtifactId();
        var artifactReference = new ArtifactReference();

        artifactReference.setName("testReference");
        artifactReference.setArtifactId(artifactId);
        artifactReference.setGroupId(groupId);
        artifactReference.setVersion("1");

        var artifactReferences = List.of(artifactReference);

        createArtifactWithReferences(groupId, secondArtifactId, artifactReferences);

        ArtifactContent artifactContent = new ArtifactContent();
        artifactContent.setContent(ARTIFACT_CONTENT);
        artifactContent.setReferences(artifactReferences);

        final VersionMetaData secondVersionMetadata = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(secondArtifactId).meta().post(artifactContent).get(3, TimeUnit.SECONDS);

        assertNotEquals(secondVersionMetadata.getContentId(), versionMetaData.getContentId());
    }

    @Test
    public void getContentByHash() throws Exception {
        // Preparation
        final String groupId = "getContentByHash";
        final String artifactId = generateArtifactId();

        String contentHash = DigestUtils.sha256Hex(ARTIFACT_CONTENT);

        createArtifact(groupId, artifactId);

        // Execution
        InputStream content = clientV3.ids().contentHashes().byContentHash(contentHash).get().get(3,
                TimeUnit.SECONDS);
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

        String referencesSerialized = SqlUtil
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
        content = clientV3.ids().contentHashes().byContentHash(contentHash).get().get(3, TimeUnit.SECONDS);
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

        ArtifactMetaData amd = createArtifact(groupId, artifactId);

        // Execution
        TestUtils.retry(() -> {
            InputStream content = clientV3.ids().globalIds().byGlobalId(amd.getGlobalId()).get().get(3,
                    TimeUnit.SECONDS);
            assertNotNull(content);

            // Assertions
            String artifactContent = IOUtils.toString(content, StandardCharsets.UTF_8);
            assertEquals(ARTIFACT_CONTENT, artifactContent);
        });
    }

    @Test
    public void getArtifactVersionMetaDataByContent() throws Exception {
        // Preparation
        final String groupId = "getArtifactVersionMetaDataByContent";
        final String artifactId = generateArtifactId();

        // Create first artifact, without references
        final ArtifactMetaData amd = createArtifact(groupId, artifactId);
        // Execution
        ArtifactContent content = new ArtifactContent();
        content.setContent(ARTIFACT_CONTENT);
        final VersionMetaData vmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .meta().post(content).get(3, TimeUnit.SECONDS);

        // Assertions
        assertEquals(amd.getGlobalId(), vmd.getGlobalId());
        assertEquals(amd.getId(), vmd.getId());
        assertEquals(amd.getContentId(), vmd.getContentId());
    }

    @Test
    public void listArtifactRules() throws Exception {
        // Preparation
        final String groupId = "listArtifactRules";
        final String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId);

        TestUtils.retry(() -> {
            final List<RuleType> emptyRules = clientV3.groups().byGroupId(groupId).artifacts()
                    .byArtifactId(artifactId).rules().get().get(3, TimeUnit.SECONDS);

            // Assertions
            assertNotNull(emptyRules);
            assertTrue(emptyRules.isEmpty());
        });

        // Execution
        createArtifactRule(groupId, artifactId, io.apicurio.registry.types.RuleType.COMPATIBILITY,
                "BACKWARD");

        TestUtils.retry(() -> {
            final List<RuleType> ruleTypes = clientV3.groups().byGroupId(groupId).artifacts()
                    .byArtifactId(artifactId).rules().get().get(3, TimeUnit.SECONDS);

            // Assertions
            assertNotNull(ruleTypes);
            assertFalse(ruleTypes.isEmpty());
        });
    }

    @Test
    public void testCompatibilityWithReferences() throws Exception {
        // Preparation
        final String groupId = "testCompatibilityWithReferences";
        final String artifactId = generateArtifactId();

        // First create the references schema
        createArtifact(groupId, artifactId, ArtifactType.AVRO, REFERENCED_SCHEMA);

        io.apicurio.registry.rest.v3.beans.ArtifactReference artifactReference = new io.apicurio.registry.rest.v3.beans.ArtifactReference();
        artifactReference.setArtifactId(artifactId);
        artifactReference.setGroupId(groupId);
        artifactReference.setVersion("1");
        artifactReference.setName("com.example.common.ItemId");

        final String secondArtifactId = generateArtifactId();
        createArtifactWithReferences(groupId, secondArtifactId, ArtifactType.AVRO, SCHEMA_WITH_REFERENCE,
                List.of(artifactReference));

        // Create rule
        createArtifactRule(groupId, secondArtifactId, io.apicurio.registry.types.RuleType.COMPATIBILITY,
                "BACKWARD");

        updateArtifactWithReferences(groupId, secondArtifactId, ArtifactType.AVRO, SCHEMA_WITH_REFERENCE,
                List.of(artifactReference));

        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete().get(3,
                TimeUnit.SECONDS);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(secondArtifactId).delete().get(3,
                TimeUnit.SECONDS);
    }

    @Test
    public void deleteArtifactRules() throws Exception {
        // Preparation
        final String groupId = "deleteArtifactRules";
        final String artifactId = generateArtifactId();

        prepareRuleTest(groupId, artifactId, io.apicurio.registry.types.RuleType.COMPATIBILITY, "BACKWARD");

        // Execution
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().delete().get(3,
                TimeUnit.SECONDS);

        // Assertions
        TestUtils.retry(() -> {
            final List<RuleType> emptyRules = clientV3.groups().byGroupId(groupId).artifacts()
                    .byArtifactId(artifactId).rules().get().get(3, TimeUnit.SECONDS);
            assertNotNull(emptyRules);
            assertTrue(emptyRules.isEmpty());
        });
    }

    @Test
    public void getArtifactRuleConfig() throws Exception {
        // Preparation
        final String groupId = "getArtifactRuleConfig";
        final String artifactId = generateArtifactId();

        prepareRuleTest(groupId, artifactId, io.apicurio.registry.types.RuleType.COMPATIBILITY, "BACKWARD");

        TestUtils.retry(() -> {
            // Execution
            final Rule rule = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                    .rules().byRule(RuleType.COMPATIBILITY.name()).get().get(3, TimeUnit.SECONDS);
            // Assertions
            assertNotNull(rule);
            assertEquals("BACKWARD", rule.getConfig());
        });
    }

    @Test
    public void updateArtifactRuleConfig() throws Exception {
        // Preparation
        final String groupId = "updateArtifactRuleConfig";
        final String artifactId = generateArtifactId();

        prepareRuleTest(groupId, artifactId, io.apicurio.registry.types.RuleType.COMPATIBILITY, "BACKWARD");

        TestUtils.retry(() -> {
            final Rule rule = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                    .rules().byRule(RuleType.COMPATIBILITY.name()).get().get(3, TimeUnit.SECONDS);
            assertNotNull(rule);
            assertEquals("BACKWARD", rule.getConfig());
        });

        final Rule toUpdate = new Rule();
        toUpdate.setType(RuleType.COMPATIBILITY);
        toUpdate.setConfig("FULL");

        // Execution
        final Rule updated = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules()
                .byRule(RuleType.COMPATIBILITY.name()).put(toUpdate).get(3, TimeUnit.SECONDS);

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
        ArtifactContent content = new ArtifactContent();
        content.setContent(updatedContent);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(content, config -> {
            config.headers.add("X-Registry-ArtifactId", artifactId);
            config.headers.add("X-Registry-Name", name);
            config.headers.add("X-Registry-Description", description);
            config.headers.add("X-Registry-Version", version);
            config.headers.add("Content-Type", "application/create.extended+json");
        }).get(3, TimeUnit.SECONDS);

        // Assertions
        assertEquals(updatedContent, IoUtil.toString(clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).get().get(3, TimeUnit.SECONDS)));

        ArtifactMetaData artifactMetaData = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).meta().get().get(3, TimeUnit.SECONDS);
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
        ArtifactContent content = new ArtifactContent();
        content.setContent(UPDATED_OPENAPI_YAML_CONTENT);
        var request = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .toPutRequestInformation(content, config -> {
                    config.headers.add("X-Registry-ArtifactId", artifactId);
                    config.headers.add("X-Registry-ArtifactType", ArtifactType.OPENAPI);
                    config.headers.add("X-Registry-Name", name);
                    config.headers.add("X-Registry-Description", description);
                    config.headers.add("X-Registry-Version", version);
                });
        // HACK to set the correct body content
        request.setStreamContent(new ByteArrayInputStream(UPDATED_OPENAPI_YAML_CONTENT.getBytes()));
        // HACK to set the correct header
        request.headers.replace("Content-Type", Set.of(ContentTypes.APPLICATION_YAML));
        anonymousAdapter.sendAsync(request, ArtifactMetaData::createFromDiscriminatorValue, new HashMap<>())
                .get(3, TimeUnit.SECONDS);

        // Assertions
        assertMultilineTextEquals(UPDATED_OPENAPI_JSON_CONTENT, IoUtil.toString(clientV3.groups()
                .byGroupId(groupId).artifacts().byArtifactId(artifactId).get().get(3, TimeUnit.SECONDS)));

        ArtifactMetaData artifactMetaData = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).meta().get().get(3, TimeUnit.SECONDS);
        assertNotNull(artifactMetaData);
        assertEquals(version, artifactMetaData.getVersion());
        assertEquals(name, artifactMetaData.getName());
        assertEquals(description, artifactMetaData.getDescription());
    }

    @Test
    public void deleteArtifactsInGroup() throws Exception {
        // Preparation
        final String groupId = "deleteArtifactsInGroup";
        final String firstArtifactId = generateArtifactId();
        final String secondArtifactId = generateArtifactId();
        createArtifact(groupId, firstArtifactId);
        createArtifact(groupId, secondArtifactId);

        final ArtifactSearchResults searchResults = clientV3.groups().byGroupId(groupId).artifacts().get()
                .get(3, TimeUnit.SECONDS);
        assertFalse(searchResults.getArtifacts().isEmpty());
        assertEquals(2, (int) searchResults.getCount());

        // Execution
        clientV3.groups().byGroupId(groupId).artifacts().delete().get(3, TimeUnit.SECONDS);

        TestUtils.retry(() -> {
            final ArtifactSearchResults deleted = clientV3.groups().byGroupId(groupId).artifacts().get()
                    .get(3, TimeUnit.SECONDS);

            // Assertions
            assertTrue(deleted.getArtifacts().isEmpty());
            assertEquals(0, (int) deleted.getCount());
        });
    }

    @Test
    public void searchArtifactsByContent() throws Exception {
        // Preparation
        final String groupId = "searchArtifactsByContent";
        final String firstArtifactId = generateArtifactId();
        final String secondArtifactId = generateArtifactId();

        String content = "{\"name\":\"" + TestUtils.generateSubject() + "\"}";
        createArtifact(groupId, firstArtifactId, "AVRO", content);
        createArtifact(groupId, secondArtifactId, "AVRO", content);

        // Execution
        final ArtifactSearchResults searchResults = clientV3.search().artifacts()
                .post(IoUtil.toStream(content)).get(3, TimeUnit.SECONDS);

        // Assertions
        assertEquals(2, searchResults.getCount());
    }

    @Test
    public void smokeGlobalRules() throws Exception {
        createGlobalRule(io.apicurio.registry.types.RuleType.COMPATIBILITY, "BACKWARD");
        createGlobalRule(io.apicurio.registry.types.RuleType.VALIDITY, "FORWARD");

        TestUtils.retry(() -> {
            final List<RuleType> globalRules = clientV3.admin().rules().get().get(3, TimeUnit.SECONDS);
            assertEquals(2, globalRules.size());
            assertTrue(globalRules.contains(RuleType.COMPATIBILITY));
            assertTrue(globalRules.contains(RuleType.VALIDITY));
        });
        clientV3.admin().rules().delete().get(3, TimeUnit.SECONDS);
        TestUtils.retry(() -> {
            final List<RuleType> updatedRules = clientV3.admin().rules().get().get(3, TimeUnit.SECONDS);
            assertEquals(0, updatedRules.size());
        });
    }

    @Test
    public void getGlobalRuleConfig() throws Exception {
        // Preparation
        createGlobalRule(io.apicurio.registry.types.RuleType.COMPATIBILITY, "BACKWARD");

        TestUtils.retry(() -> {
            // Execution
            final Rule globalRuleConfig = clientV3.admin().rules().byRule(RuleType.COMPATIBILITY.name()).get()
                    .get(3, TimeUnit.SECONDS);
            // Assertions
            assertEquals(globalRuleConfig.getConfig(), "BACKWARD");
        });
    }

    @Test
    public void updateGlobalRuleConfig() throws Exception {
        // Preparation
        createGlobalRule(io.apicurio.registry.types.RuleType.COMPATIBILITY, "BACKWARD");

        TestUtils.retry(() -> {
            final Rule globalRuleConfig = clientV3.admin().rules().byRule(RuleType.COMPATIBILITY.name()).get()
                    .get(3, TimeUnit.SECONDS);
            assertEquals(globalRuleConfig.getConfig(), "BACKWARD");
        });

        final Rule toUpdate = new Rule();
        toUpdate.setType(RuleType.COMPATIBILITY);
        toUpdate.setConfig("FORWARD");

        // Execution
        final Rule updated = clientV3.admin().rules().byRule(RuleType.COMPATIBILITY.name()).put(toUpdate)
                .get(3, TimeUnit.SECONDS);

        // Assertions
        assertEquals(updated.getConfig(), "FORWARD");
    }

    @Test
    public void deleteGlobalRule() throws Exception {
        // Preparation
        createGlobalRule(io.apicurio.registry.types.RuleType.COMPATIBILITY, "BACKWARD");

        TestUtils.retry(() -> {
            final Rule globalRuleConfig = clientV3.admin().rules().byRule(RuleType.COMPATIBILITY.name()).get()
                    .get(3, TimeUnit.SECONDS);
            assertEquals(globalRuleConfig.getConfig(), "BACKWARD");
        });

        // Execution
        clientV3.admin().rules().byRule(RuleType.COMPATIBILITY.name()).delete().get(3, TimeUnit.SECONDS);

        TestUtils.retry(() -> {
            final List<RuleType> ruleTypes = clientV3.admin().rules().get().get(3, TimeUnit.SECONDS);

            // Assertions
            assertEquals(0, ruleTypes.size());
        });
    }

    @Test
    @DisabledIfEnvironmentVariable(named = AbstractRegistryTestBase.CURRENT_ENV, matches = AbstractRegistryTestBase.CURRENT_ENV_MAS_REGEX)
    public void testDefaultGroup() throws Exception {
        String nullDefaultGroup = "default";
        String artifactId1 = "testDefaultGroup-" + UUID.randomUUID().toString();
        createArtifact(nullDefaultGroup, artifactId1);
        verifyGroupNullInMetadata(artifactId1, ARTIFACT_CONTENT);

        String defaultDefaultGroup = "default";
        String artifactId2 = "testDefaultGroup-" + UUID.randomUUID().toString();
        createArtifact(defaultDefaultGroup, artifactId2);
        verifyGroupNullInMetadata(artifactId2, ARTIFACT_CONTENT);

        String dummyGroup = "dummy";
        String artifactId3 = "testDefaultGroup-" + UUID.randomUUID().toString();
        createArtifact(dummyGroup, artifactId3);

        ArtifactSearchResults result = clientV3.search().artifacts().get(config -> {
            config.queryParameters.limit = 100;
        }).get(3, TimeUnit.SECONDS);

        SearchedArtifact artifact1 = result.getArtifacts().stream().filter(s -> s.getId().equals(artifactId1))
                .findFirst().orElseThrow();

        assertNull(artifact1.getGroupId());

        SearchedArtifact artifact2 = result.getArtifacts().stream().filter(s -> s.getId().equals(artifactId2))
                .findFirst().orElseThrow();

        assertNull(artifact2.getGroupId());

        SearchedArtifact artifact3 = result.getArtifacts().stream().filter(s -> s.getId().equals(artifactId3))
                .findFirst().orElseThrow();

        assertEquals(dummyGroup, artifact3.getGroupId());

    }

    private void verifyGroupNullInMetadata(String artifactId, String content) throws Exception {
        ArtifactMetaData meta = clientV3.groups().byGroupId("default").artifacts().byArtifactId(artifactId)
                .meta().get().get(3, TimeUnit.SECONDS);
        assertNull(meta.getGroupId());

        VersionMetaData vmeta = clientV3.groups().byGroupId("default").artifacts().byArtifactId(artifactId)
                .versions().byVersion(meta.getVersion()).meta().get().get(3, TimeUnit.SECONDS);
        assertNull(vmeta.getGroupId());

        ArtifactContent artifactContent = new ArtifactContent();
        artifactContent.setContent(content);
        vmeta = clientV3.groups().byGroupId("default").artifacts().byArtifactId(artifactId).versions()
                .post(artifactContent).get(3, TimeUnit.SECONDS);
        assertNull(vmeta.getGroupId());

        clientV3.groups().byGroupId("default").artifacts().get().get(3, TimeUnit.SECONDS).getArtifacts()
                .stream().filter(s -> s.getId().equals(artifactId)).forEach(s -> assertNull(s.getGroupId()));

    }

    private ArtifactMetaData createArtifact(String groupId, String artifactId) throws Exception {
        ArtifactContent content = new ArtifactContent();
        content.setContent(ARTIFACT_CONTENT);
        final ArtifactMetaData created = clientV3.groups().byGroupId(groupId).artifacts()
                .post(content, config -> {
                    config.queryParameters.canonical = false;
                    config.queryParameters.ifExists = "FAIL";
                    config.headers.add("X-Registry-ArtifactId", artifactId);
                    config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
                    config.headers.add("X-Registry-Name", artifactId);
                }).get(3, TimeUnit.SECONDS);
        return checkArtifact(groupId, artifactId, created);
    }

    private ArtifactMetaData createArtifactWithReferences(String groupId, String artifactId,
            List<ArtifactReference> artifactReferences) throws Exception {
        ArtifactContent content = new ArtifactContent();
        content.setContent(ARTIFACT_CONTENT);
        content.setReferences(artifactReferences);
        final ArtifactMetaData created = clientV3.groups().byGroupId(groupId).artifacts()
                .post(content, config -> {
                    config.queryParameters.canonical = false;
                    config.queryParameters.ifExists = "FAIL";
                    config.headers.add("X-Registry-ArtifactId", artifactId);
                    config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
                    config.headers.add("X-Registry-Name", artifactId);
                }).get(3, TimeUnit.SECONDS);

        return checkArtifact(groupId, artifactId, created);
    }

    @NotNull
    private ArtifactMetaData checkArtifact(String groupId, String artifactId, ArtifactMetaData created)
            throws Exception {
        assertNotNull(created);
        if (groupId == null || groupId.equals("default")) {
            assertNull(created.getGroupId());
        } else {
            assertEquals(groupId, created.getGroupId());
        }
        assertEquals(artifactId, created.getId());

        return created;
    }

    private ArtifactMetaData createOpenAPIArtifact(String groupId, String artifactId) throws Exception {
        ArtifactContent content = new ArtifactContent();
        content.setContent(ARTIFACT_OPENAPI_JSON_CONTENT);

        final ArtifactMetaData created = clientV3.groups().byGroupId(groupId).artifacts()
                .post(content, config -> {
                    config.queryParameters.canonical = false;
                    config.queryParameters.ifExists = "FAIL";
                    config.headers.add("X-Registry-ArtifactId", artifactId);
                    config.headers.add("X-Registry-ArtifactType", ArtifactType.OPENAPI);
                    config.headers.add("Content-Type", "application/create.extended+json");
                }).get(3, TimeUnit.SECONDS);
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
        List<RoleMapping> roleMappings = clientV3.admin().roleMappings().get().get(3, TimeUnit.SECONDS);
        Assertions.assertTrue(roleMappings.isEmpty());

        // Add
        RoleMapping mapping = new RoleMapping();
        mapping.setPrincipalId("TestUser");
        mapping.setRole(RoleType.DEVELOPER);
        clientV3.admin().roleMappings().post(mapping).get(3, TimeUnit.SECONDS);

        // Verify the mapping was added.
        TestUtils.retry(() -> {
            RoleMapping roleMapping = clientV3.admin().roleMappings().byPrincipalId("TestUser").get().get(3,
                    TimeUnit.SECONDS);
            Assertions.assertEquals("TestUser", roleMapping.getPrincipalId());
            Assertions.assertEquals(RoleType.DEVELOPER, roleMapping.getRole());
        });
        TestUtils.retry(() -> {
            List<RoleMapping> mappings = clientV3.admin().roleMappings().get().get(3, TimeUnit.SECONDS);
            Assertions.assertEquals(1, mappings.size());
            Assertions.assertEquals("TestUser", mappings.get(0).getPrincipalId());
            Assertions.assertEquals(RoleType.DEVELOPER, mappings.get(0).getRole());
        });

        // Try to add the rule again - should get a 409
        TestUtils.retry(() -> {
            var executionException = Assertions.assertThrows(ExecutionException.class, () -> {
                clientV3.admin().roleMappings().post(mapping).get(3, TimeUnit.SECONDS);
            });
            Assertions.assertNotNull(executionException.getCause());
            Assertions.assertEquals(ApiException.class, executionException.getCause().getClass());
            Assertions.assertEquals(409,
                    ((ApiException) executionException.getCause()).getResponseStatusCode());
        });

        // Add another mapping
        mapping.setPrincipalId("TestUser2");
        mapping.setRole(RoleType.ADMIN);
        clientV3.admin().roleMappings().post(mapping).get(3, TimeUnit.SECONDS);

        // Get the list of mappings (should be 2 of them)
        TestUtils.retry(() -> {
            List<RoleMapping> mappings = clientV3.admin().roleMappings().get().get(3, TimeUnit.SECONDS);
            Assertions.assertEquals(2, mappings.size());
        });

        // Get a single mapping by principal
        RoleMapping tu2Mapping = clientV3.admin().roleMappings().byPrincipalId("TestUser2").get().get(3,
                TimeUnit.SECONDS);
        Assertions.assertEquals("TestUser2", tu2Mapping.getPrincipalId());
        Assertions.assertEquals(RoleType.ADMIN, tu2Mapping.getRole());

        // Update a mapping
        UpdateRole updated = new UpdateRole();
        updated.setRole(RoleType.READ_ONLY);
        clientV3.admin().roleMappings().byPrincipalId("TestUser").put(updated).get(3, TimeUnit.SECONDS);

        // Get a single (updated) mapping
        TestUtils.retry(() -> {
            RoleMapping tum = clientV3.admin().roleMappings().byPrincipalId("TestUser").get().get(3,
                    TimeUnit.SECONDS);
            Assertions.assertEquals("TestUser", tum.getPrincipalId());
            Assertions.assertEquals(RoleType.READ_ONLY, tum.getRole());
        });

        // Try to update a role mapping that doesn't exist
        var executionException = Assertions.assertThrows(ExecutionException.class, () -> {
            UpdateRole updated2 = new UpdateRole();
            updated2.setRole(RoleType.ADMIN);
            clientV3.admin().roleMappings().byPrincipalId("UnknownPrincipal").put(updated2).get(3,
                    TimeUnit.SECONDS);
        });

        // RoleMappingNotFoundException
        Assertions.assertNotNull(executionException.getCause());
        Assertions.assertEquals(io.apicurio.registry.rest.client.models.Error.class,
                executionException.getCause().getClass());
        Assertions.assertEquals(404,
                ((io.apicurio.registry.rest.client.models.Error) executionException.getCause())
                        .getErrorCode());
        Assertions.assertEquals("RoleMappingNotFoundException",
                ((io.apicurio.registry.rest.client.models.Error) executionException.getCause()).getName());

        // Delete a role mapping
        clientV3.admin().roleMappings().byPrincipalId("TestUser2").delete().get(3, TimeUnit.SECONDS);

        // Get the (deleted) mapping by name (should fail with a 404)
        TestUtils.retry(() -> {
            var executionException2 = Assertions.assertThrows(ExecutionException.class, () -> {
                clientV3.admin().roleMappings().byPrincipalId("TestUser2").get().get(3, TimeUnit.SECONDS);
            });
            // RoleMappingNotFoundException
            Assertions.assertNotNull(executionException2.getCause());
            Assertions.assertEquals(io.apicurio.registry.rest.client.models.Error.class,
                    executionException2.getCause().getClass());
            Assertions.assertEquals(404,
                    ((io.apicurio.registry.rest.client.models.Error) executionException2.getCause())
                            .getErrorCode());
            Assertions.assertEquals("RoleMappingNotFoundException",
                    ((io.apicurio.registry.rest.client.models.Error) executionException2.getCause())
                            .getName());
        });

        // Get the list of mappings (should be 1 of them)
        TestUtils.retry(() -> {
            List<RoleMapping> mappings = clientV3.admin().roleMappings().get().get(3, TimeUnit.SECONDS);
            Assertions.assertEquals(1, mappings.size());
            Assertions.assertEquals("TestUser", mappings.get(0).getPrincipalId());
        });

        // Clean up
        clientV3.admin().roleMappings().byPrincipalId("TestUser").delete().get(3, TimeUnit.SECONDS);
    }

    @Test
    public void testConfigProperties() throws Exception {
        String property1Name = "registry.ccompat.legacy-id-mode.enabled";
        String property2Name = "registry.rest.artifact.deletion.enabled";

        // Start with all default values
        List<ConfigurationProperty> configProperties = clientV3.admin().config().properties().get().get(3,
                TimeUnit.SECONDS);
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
        clientV3.admin().config().properties().byPropertyName(property1Name).put(updateProp).get(3,
                TimeUnit.SECONDS);

        // Verify the property was set.
        ConfigurationProperty prop = clientV3.admin().config().properties().byPropertyName(property1Name)
                .get().get(3, TimeUnit.SECONDS);
        Assertions.assertEquals(property1Name, prop.getName());
        Assertions.assertEquals("true", prop.getValue());

        List<ConfigurationProperty> properties = clientV3.admin().config().properties().get().get(3,
                TimeUnit.SECONDS);
        prop = properties.stream().filter(cp -> cp.getName().equals(property1Name)).findFirst().get();
        Assertions.assertEquals(property1Name, prop.getName());
        Assertions.assertEquals("true", prop.getValue());

        // Set another property
        updateProp.setValue("false");
        clientV3.admin().config().properties().byPropertyName(property2Name).put(updateProp).get(3,
                TimeUnit.SECONDS);

        // Verify the property was set.
        prop = clientV3.admin().config().properties().byPropertyName(property2Name).get().get(3,
                TimeUnit.SECONDS);
        Assertions.assertEquals(property2Name, prop.getName());
        Assertions.assertEquals("false", prop.getValue());

        properties = clientV3.admin().config().properties().get().get(3, TimeUnit.SECONDS);
        prop = properties.stream().filter(cp -> cp.getName().equals(property2Name)).findFirst().get();
        Assertions.assertEquals("false", prop.getValue());

        // Reset a config property
        clientV3.admin().config().properties().byPropertyName(property2Name).delete().get(3,
                TimeUnit.SECONDS);

        // Verify the property was reset.
        prop = clientV3.admin().config().properties().byPropertyName(property2Name).get().get(3,
                TimeUnit.SECONDS);
        Assertions.assertEquals(property2Name, prop.getName());
        Assertions.assertEquals("true", prop.getValue());

        properties = clientV3.admin().config().properties().get().get(3, TimeUnit.SECONDS);
        prop = properties.stream().filter(cp -> cp.getName().equals(property2Name)).findFirst().get();
        Assertions.assertEquals("true", prop.getValue());

        // Reset the other property
        clientV3.admin().config().properties().byPropertyName(property1Name).delete().get(3,
                TimeUnit.SECONDS);

        // Verify the property was reset.
        prop = clientV3.admin().config().properties().byPropertyName(property1Name).get().get(3,
                TimeUnit.SECONDS);
        Assertions.assertEquals(property1Name, prop.getName());
        Assertions.assertEquals("false", prop.getValue());

        properties = clientV3.admin().config().properties().get().get(3, TimeUnit.SECONDS);
        prop = properties.stream().filter(cp -> cp.getName().equals(property1Name)).findFirst().get();
        Assertions.assertEquals(property1Name, prop.getName());
        Assertions.assertEquals("false", prop.getValue());

        // Try to set a config property that doesn't exist.
        var executionException1 = Assertions.assertThrows(ExecutionException.class, () -> {
            updateProp.setValue("foobar");
            clientV3.admin().config().properties().byPropertyName("property-does-not-exist").put(updateProp)
                    .get(3, TimeUnit.SECONDS);
        });
        // ConfigPropertyNotFoundException
        Assertions.assertNotNull(executionException1.getCause());
        Assertions.assertEquals(io.apicurio.registry.rest.client.models.Error.class,
                executionException1.getCause().getClass());
        Assertions.assertEquals(404,
                ((io.apicurio.registry.rest.client.models.Error) executionException1.getCause())
                        .getErrorCode());
        Assertions.assertEquals("ConfigPropertyNotFoundException",
                ((io.apicurio.registry.rest.client.models.Error) executionException1.getCause()).getName());

        // Try to set a Long property to "foobar" (should be invalid type)
        var executionException2 = Assertions.assertThrows(ExecutionException.class, () -> {
            updateProp.setValue("foobar");
            clientV3.admin().config().properties().byPropertyName("registry.download.href.ttl")
                    .put(updateProp).get(3, TimeUnit.SECONDS);
        });
        // InvalidPropertyValueException
        Assertions.assertNotNull(executionException2.getCause());
        Assertions.assertEquals(ApiException.class, executionException2.getCause().getClass());
        Assertions.assertEquals(400, ((ApiException) executionException2.getCause()).getResponseStatusCode());
    }

    @Test
    public void testForceArtifactType() throws Exception {
        var artifactContent = resourceToInputStream("sample.wsdl");

        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        ArtifactContent content = new ArtifactContent();
        content.setContent(IOUtils.toString(artifactContent));
        /* var postReq = */clientV3.groups().byGroupId(groupId).artifacts().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", artifactId);
            config.headers.add("X-Registry-ArtifactType", ArtifactType.AVRO);
        }).get(3, TimeUnit.SECONDS);

        var meta = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get()
                .get(3, TimeUnit.SECONDS);

        assertEquals(ArtifactType.AVRO, meta.getType());

        assertTrue(clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get()
                .get(3, TimeUnit.SECONDS).readAllBytes().length > 0);
    }

    @Test
    public void testClientRateLimitError() {
        TooManyRequestsMock mock = new TooManyRequestsMock();
        mock.start();
        try {
            var adapter = new OkHttpRequestAdapter(new AnonymousAuthenticationProvider());
            adapter.setBaseUrl(mock.getMockUrl());
            io.apicurio.registry.rest.client.RegistryClient client = new io.apicurio.registry.rest.client.RegistryClient(
                    adapter);

            var executionException1 = Assertions.assertThrows(ExecutionException.class, () -> client.groups()
                    .byGroupId("test").artifacts().byArtifactId("test").get().get(30, TimeUnit.SECONDS));
            Assertions.assertNotNull(executionException1.getCause());
            Assertions.assertEquals(ApiException.class, executionException1.getCause().getClass());
            Assertions.assertEquals(429,
                    ((ApiException) executionException1.getCause()).getResponseStatusCode());

            ArtifactContent content = new ArtifactContent();
            content.setContent("{}");
            var executionException2 = Assertions.assertThrows(ExecutionException.class,
                    () -> client.groups().byGroupId("default").artifacts().post(content, config -> {
                        config.headers.add("X-Registry-ArtifactId", "aaa");
                    }).get(30, TimeUnit.SECONDS));
            Assertions.assertNotNull(executionException2.getCause());
            Assertions.assertEquals(ApiException.class, executionException2.getCause().getClass());
            Assertions.assertEquals(429,
                    ((ApiException) executionException2.getCause()).getResponseStatusCode());

            var executionException3 = Assertions.assertThrows(ExecutionException.class,
                    () -> client.ids().globalIds().byGlobalId(5L).get().get(30, TimeUnit.SECONDS));
            Assertions.assertNotNull(executionException3.getCause());
            Assertions.assertEquals(ApiException.class, executionException3.getCause().getClass());
            Assertions.assertEquals(429,
                    ((ApiException) executionException3.getCause()).getResponseStatusCode());
        } finally {
            mock.stop();
        }
    }

    @Test
    public void testGetArtifactVersionByContent_DuplicateContent() throws Exception {
        // Preparation
        final String groupId = "testGetArtifactVersionByContent_DuplicateContent";
        final String artifactId = generateArtifactId();

        final ArtifactMetaData v1md = createArtifact(groupId, artifactId);

        ArtifactContent content = new ArtifactContent();
        content.setContent(ARTIFACT_CONTENT);

        final VersionMetaData v2md = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().post(content, config -> {
                    config.headers.add("X-Registry-ArtifactId", artifactId);
                }).get(3, TimeUnit.SECONDS);

        // Execution
        final VersionMetaData vmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .meta().post(content).get(3, TimeUnit.SECONDS);

        // Assertions
        assertNotEquals(v1md.getGlobalId(), v2md.getGlobalId());
        assertEquals(v1md.getContentId(), v2md.getContentId());

        assertEquals(v2md.getGlobalId(), vmd.getGlobalId());
        assertEquals(v2md.getId(), vmd.getId());
        assertEquals(v2md.getContentId(), vmd.getContentId());
    }
}
