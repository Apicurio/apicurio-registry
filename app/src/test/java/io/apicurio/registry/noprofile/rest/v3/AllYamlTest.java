package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.Error;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class AllYamlTest extends AbstractResourceTestBase {

    private static String YAML_CONTENT = "openapi: 3.0.2\n" +
            "info:\n" +
            "    title: Empty API\n" +
            "    version: 1.0.0\n" +
            "    description: Just an empty API.\n" +
            "paths:\n" +
            "    /test:\n" +
            "        get:\n" +
            "            responses:\n" +
            "                '200':\n" +
            "                    content:\n" +
            "                        application/json:\n" +
            "                            schema:\n" +
            "                                type: string\n" +
            "                    description: Success.\n" +
            "            operationId: test\n";
    private static String YAML_CONTENT_V2 = YAML_CONTENT.replace("Empty API", "Empty API (v2)");
    private static String YAML_CONTENT_WITH_REF = "openapi: 3.0.2\n" +
            "info:\n" +
            "    title: Empty API\n" +
            "    version: 1.0.0\n" +
            "    description: Just an empty API.\n" +
            "paths:\n" +
            "    /test:\n" +
            "        get:\n" +
            "            responses:\n" +
            "                '200':\n" +
            "                    content:\n" +
            "                        application/json:\n" +
            "                            schema:\n" +
            "                                '$ref': 'http://example.com/other-types.json#/components/schemas/MissingType'\n" +
            "                    description: Success.\n" +
            "            operationId: test\n";

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

}
