package io.apicurio.registry.customTypes;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactTypeInfo;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class AbstractCustomArtifactTypesTest extends AbstractResourceTestBase {

    private static final String RAML_CONTENT = """
#%RAML 1.0
title: Mobile Order API
baseUri: http://localhost:8081/api
version: 1.0

uses:
  assets: assets.lib.raml

annotationTypes:
  monitoringInterval:
    type: integer

/orders:
  displayName: Orders
  get:
    is: [ assets.paging ]
    (monitoringInterval): 30
    description: Lists all orders of a specific user
    queryParameters:
      userId:
        type: string
        description: use to query all orders of a user
  post:
  /{orderId}:
    get:
      responses:
        200:
          body:
            application/json:
              type: assets.Order
            """;

    private static String minifyContent(String content) {
        return content.replaceAll("(?m)^\s*$\n?", "");
    }

    @Test
    public void testArtifactTypeList() {
        List<ArtifactTypeInfo> infos = clientV3.admin().config().artifactTypes().get();
        Assertions.assertNotNull(infos);
        Assertions.assertFalse(infos.isEmpty());
        Assertions.assertEquals(1, infos.size());
        Assertions.assertEquals(Set.of("RAML"), infos.stream().map(info -> info.getName()).collect(Collectors.toSet()));
    }

    @Test
    public void testCreateRAMLArtifact() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, "RAML", RAML_CONTENT, ContentTypes.APPLICATION_YAML);
        createArtifact.getFirstVersion().setVersion("1.0");

        CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Assertions.assertNotNull(car);
        Assertions.assertNotNull(car.getArtifact());
        Assertions.assertNotNull(car.getVersion());
        Assertions.assertEquals("RAML", car.getArtifact().getArtifactType());
    }

    @Test
    public void testContentAccepter() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        // Same as "testCreateRAMLArtifact" but don't provide the artifact type.  As a result, the
        // RAML ContentAccepter will be called and should discover that the content being provided
        // is of type "RAML"
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, null, RAML_CONTENT, ContentTypes.APPLICATION_YAML);
        createArtifact.getFirstVersion().setVersion("1.0");

        CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Assertions.assertNotNull(car);
        Assertions.assertNotNull(car.getArtifact());
        Assertions.assertNotNull(car.getVersion());
        // The server should have discovered (from the content) that the artifact type is RAML
        Assertions.assertEquals("RAML", car.getArtifact().getArtifactType());
    }

    @Test
    public void testContentCanonicalizer() {
        String content = RAML_CONTENT.replace("Mobile Order API", "testCreateRAMLArtifact");
        String minContent = minifyContent(content);

        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, "RAML", content, ContentTypes.APPLICATION_YAML);
        createArtifact.getFirstVersion().setVersion("1.0");

        CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Assertions.assertNotNull(car);
        Assertions.assertNotNull(car.getArtifact());
        Assertions.assertNotNull(car.getVersion());
        Assertions.assertEquals("RAML", car.getArtifact().getArtifactType());

        // Search for the exact content we added.  Should always work.
        InputStream body = new ByteArrayInputStream(content.getBytes());
        VersionSearchResults results = clientV3.search().versions().post(body, ContentTypes.APPLICATION_YAML);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());

        // Search for the minified content.  Will not work unless we set canonical=true
        body = new ByteArrayInputStream(minContent.getBytes());
        results = clientV3.search().versions().post(body, ContentTypes.APPLICATION_YAML);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(0, results.getCount());

        // Search for the minified content again, this time with canonical=true - should work.
        body = new ByteArrayInputStream(minContent.getBytes());
        results = clientV3.search().versions().post(body, ContentTypes.APPLICATION_YAML, config -> {
            config.queryParameters.artifactType = "RAML";
            config.queryParameters.canonical = true;
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());
    }

    @Test
    public void testContentValidator() {
        String groupId = TestUtils.generateGroupId();

        // Create the group
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        // Configure the Validity rule for the group
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig(ValidityLevel.FULL.name());
        clientV3.groups().byGroupId(groupId).rules().post(createRule);

        String validContent = RAML_CONTENT;
        String invalidContent = RAML_CONTENT.replace("#%RAML 1.0", "#Random YAML");

        // Create from valid content
        String validArtifactId = TestUtils.generateArtifactId();
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(validArtifactId, "RAML", validContent, ContentTypes.APPLICATION_YAML);
        createArtifact.getFirstVersion().setVersion("1.0");
        CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Assertions.assertNotNull(car);
        Assertions.assertNotNull(car.getArtifact());
        Assertions.assertNotNull(car.getVersion());
        Assertions.assertEquals("RAML", car.getArtifact().getArtifactType());

        // Create from invalid content
        Assertions.assertThrows(RuleViolationProblemDetails.class, () -> {
            String invalidArtifactId = TestUtils.generateArtifactId();
            CreateArtifact createArtifactInvalid = TestUtils.clientCreateArtifact(validArtifactId, "RAML", invalidContent, ContentTypes.APPLICATION_YAML);
            createArtifactInvalid.getFirstVersion().setVersion("1.0");
            clientV3.groups().byGroupId(groupId).artifacts().post(createArtifactInvalid);
        });
    }

}