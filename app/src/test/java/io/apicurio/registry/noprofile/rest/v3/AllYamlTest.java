package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.Error;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

@QuarkusTest
public class AllYamlTest extends AbstractResourceTestBase {

    private static String YAML_CONTENT = """
            openapi: 3.0.2
            info:
                title: Empty API
                version: 1.0.0
                description: Just an empty API.
            paths:
                /test:
                    get:
                        responses:
                            '200':
                                content:
                                    application/json:
                                        schema:
                                            type: string
                                description: Success.
                        operationId: test
            """;
    private static String YAML_CONTENT_V2 = YAML_CONTENT.replace("Empty API", "Empty API (v2)");
    private static String YAML_CONTENT_WITH_REF = """
            openapi: 3.0.2
            info:
                title: Empty API
                version: 1.0.0
                description: Just an empty API.
            paths:
                /test:
                    get:
                        responses:
                            '200':
                                content:
                                    application/json:
                                        schema:
                                            '$ref': 'http://example.com/other-types.json#/components/schemas/MissingType'
                                description: Success.
                        operationId: test
            """;
    private static String JSON_CONTENT = """
          {
              "openapi": "3.0.2",
              "info": {
                  "title": "Empty API",
                  "version": "1.0.0",
                  "description": "Just an empty API."
              },
              "paths": {
                  "/test": {
                      "get": {
                          "responses": {
                              "200": {
                                  "content": {
                                      "application/json": {
                                          "schema": {
                                              "type": "string"
                                          }
                                      }
                                  },
                                  "description": "Success."
                              }
                          },
                          "operationId": "test"
                      }
                  }
              }
          }""";

    @Test
    public void testCreateYamlArtifact() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.OPENAPI, YAML_CONTENT, ContentTypes.APPLICATION_YAML);
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
    }

    @Test
    public void testCreateYamlArtifactDiscoverType() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, null, YAML_CONTENT, ContentTypes.APPLICATION_YAML);
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
    }

    @Test
    public void testCreateYamlArtifactWithValidity() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        Rule createRule = new Rule();
        createRule.setType(RuleType.VALIDITY);
        createRule.setConfig(ValidityLevel.FULL.name());
        clientV3.admin().rules().post(createRule);

        try {
            CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.OPENAPI, YAML_CONTENT, ContentTypes.APPLICATION_YAML);
            clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        } catch (Error e) {
            System.out.println("ERROR: " + e.getDetail());
            e.getCause().printStackTrace();
            throw e;
        }
    }

    @Test
    public void testUpdateYamlArtifact() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.OPENAPI, YAML_CONTENT, ContentTypes.APPLICATION_YAML);
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        CreateVersion createVersion = TestUtils.clientCreateVersion(YAML_CONTENT_V2, ContentTypes.APPLICATION_YAML);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().post(createVersion);
    }

    @Test
    public void testUpdateYamlArtifactWithCompatibility() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.OPENAPI, YAML_CONTENT, ContentTypes.APPLICATION_YAML);
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        // Enable the compatibility rule for the artifact
        Rule createRule = new Rule();
        createRule.setType(RuleType.COMPATIBILITY);
        createRule.setConfig(CompatibilityLevel.FULL.name());
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().post(createRule);

        // Now create a new version
        CreateVersion createVersion = TestUtils.clientCreateVersion(YAML_CONTENT_V2, ContentTypes.APPLICATION_YAML);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().post(createVersion);
    }

    @Test
    public void testUpdateYamlArtifactWithIntegrity() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.OPENAPI, YAML_CONTENT, ContentTypes.APPLICATION_YAML);
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        // Enable the compatibility rule for the artifact
        Rule createRule = new Rule();
        createRule.setType(RuleType.INTEGRITY);
        createRule.setConfig(IntegrityLevel.FULL.name());
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().post(createRule);

        // Now create a new version with a missing $ref
        CreateVersion createVersion = TestUtils.clientCreateVersion(YAML_CONTENT_WITH_REF, ContentTypes.APPLICATION_YAML);
        Assertions.assertThrows(Exception.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().post(createVersion);
        });
    }

    @Test
    public void testCanonicalContent() throws Exception {
        String testId = UUID.randomUUID().toString();
        String yamlContent = YAML_CONTENT.replace("Empty API", testId);
        String jsonContent = JSON_CONTENT.replace("Empty API", testId);

        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.OPENAPI, yamlContent, ContentTypes.APPLICATION_YAML);
        CreateArtifactResponse response = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        // Search for the version by its content as YAML
        VersionSearchResults results = clientV3.search().versions().post(IoUtil.toStream(yamlContent), ContentTypes.APPLICATION_YAML);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals(response.getVersion().getArtifactId(), results.getVersions().get(0).getArtifactId());
        Assertions.assertEquals(response.getVersion().getGroupId(), results.getVersions().get(0).getGroupId());
        Assertions.assertEquals(response.getVersion().getVersion(), results.getVersions().get(0).getVersion());
        Assertions.assertEquals(response.getVersion().getGlobalId(), results.getVersions().get(0).getGlobalId());

        // Search for the version by its canonical content as YAML
        results = clientV3.search().versions().post(IoUtil.toStream(yamlContent), ContentTypes.APPLICATION_YAML, config -> {
            config.queryParameters.canonical = true;
            config.queryParameters.artifactType = ArtifactType.OPENAPI;
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals(response.getVersion().getArtifactId(), results.getVersions().get(0).getArtifactId());
        Assertions.assertEquals(response.getVersion().getGroupId(), results.getVersions().get(0).getGroupId());
        Assertions.assertEquals(response.getVersion().getVersion(), results.getVersions().get(0).getVersion());
        Assertions.assertEquals(response.getVersion().getGlobalId(), results.getVersions().get(0).getGlobalId());

        // Search for the version again by its canonical content as JSON
        results = clientV3.search().versions().post(IoUtil.toStream(jsonContent), ContentTypes.APPLICATION_JSON, config -> {
            config.queryParameters.canonical = true;
            config.queryParameters.artifactType = ArtifactType.OPENAPI;
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals(response.getVersion().getArtifactId(), results.getVersions().get(0).getArtifactId());
        Assertions.assertEquals(response.getVersion().getGroupId(), results.getVersions().get(0).getGroupId());
        Assertions.assertEquals(response.getVersion().getVersion(), results.getVersions().get(0).getVersion());
        Assertions.assertEquals(response.getVersion().getGlobalId(), results.getVersions().get(0).getGlobalId());
    }

}
