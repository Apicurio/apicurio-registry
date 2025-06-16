package io.apicurio.registry.customTypes;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.ArtifactTypeInfo;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.HandleReferencesType;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.IoUtil;
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
version: "1.0"

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
            application/xml:
              type: ~include schemas/order.xsd
            """;

    private static String canonicalizeContent(String content) {
        String converted = content
                .replace("use to query all orders of a user", "USE TO QUERY ALL ORDERS OF A USER")
                .replace("Lists all orders of a specific user", "LIST ALL ORDERS OF A SPECIFIC USER");
        return converted;
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
        String content = RAML_CONTENT.replace("Mobile Order API", "testContentCanonicalizer");
        String canonicalizedContent = canonicalizeContent(content);

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

        // Search for the canonicalized content.  Will not work unless we set canonical=true
        body = new ByteArrayInputStream(canonicalizedContent.getBytes());
        results = clientV3.search().versions().post(body, ContentTypes.APPLICATION_YAML);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(0, results.getCount());

        // Search for the canonicalized content again, this time with canonical=true - should work.
        body = new ByteArrayInputStream(canonicalizedContent.getBytes());
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
            CreateArtifact createArtifactInvalid = TestUtils.clientCreateArtifact(invalidArtifactId, "RAML", invalidContent, ContentTypes.APPLICATION_YAML);
            createArtifactInvalid.getFirstVersion().setVersion("1.0");
            clientV3.groups().byGroupId(groupId).artifacts().post(createArtifactInvalid);
        });
    }

    @Test
    public void testCompatibilityChecker() {
        String groupId = TestUtils.generateGroupId();

        // Create the group
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        // Configure the Compatibility rule for the group
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.COMPATIBILITY);
        createRule.setConfig(CompatibilityLevel.BACKWARD.name());
        clientV3.groups().byGroupId(groupId).rules().post(createRule);

        String v1Content = RAML_CONTENT;
        String v2Content = RAML_CONTENT.replace("version: \"1.0\"", "version: \"2.0\"");

        // Create v1
        String artifactId = TestUtils.generateArtifactId();
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, "RAML", v1Content, ContentTypes.APPLICATION_YAML);
        createArtifact.getFirstVersion().setVersion("1.0");
        CreateArtifactResponse v1Response = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Assertions.assertNotNull(v1Response);
        Assertions.assertNotNull(v1Response.getArtifact());
        Assertions.assertNotNull(v1Response.getVersion());
        Assertions.assertEquals("RAML", v1Response.getArtifact().getArtifactType());

        // Create with v1 content again - should fail compatibility
        Assertions.assertThrows(RuleViolationProblemDetails.class, () -> {
            CreateVersion createVersion2 = TestUtils.clientCreateVersion(v1Content, ContentTypes.APPLICATION_YAML);
            createVersion2.setVersion("2.0");
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().post(createVersion2);
        });

        // Create with v2 content - should pass compatibility
        CreateVersion createVersion2 = TestUtils.clientCreateVersion(v2Content, ContentTypes.APPLICATION_YAML);
        createVersion2.setVersion("2.0");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().post(createVersion2);
    }

    private static final String DEREFERENCED_RAML_CONTENT = """
---
title: "Mobile Order API"
baseUri: "http://localhost:8081/api"
version: "1.0"
uses:
  assets: "assets.lib.raml"
annotationTypes:
  monitoringInterval:
    type: "integer"
/orders:
  displayName: "Orders"
  get:
    is:
    - "assets.paging"
    (monitoringInterval): 30
    description: "Lists all orders of a specific user"
    queryParameters:
      userId:
        type: "string"
        description: "use to query all orders of a user"
  post: null
  /{orderId}:
    get:
      responses:
        "200":
          body:
            application/json:
              type: "assets.Order"
            application/xml:
              type: "ORDER_XSD_CONTENT"
    """;

    @Test
    public void testContentDereferencer() {
        String groupId = TestUtils.generateGroupId();

        // Create the "order.xsd" artifact that we will reference.
        // Note: normally this would be an "XSD" artifact, but this test disables all the built-in types, so
        //       it's fine if we just say it's a RAML file.  For testing it doesn't matter.
        CreateArtifact createOrderXsd = TestUtils.clientCreateArtifact("order-xsd", "RAML", "ORDER_XSD_CONTENT", ContentTypes.APPLICATION_XML);
        createOrderXsd.getFirstVersion().setVersion("1.0");
        clientV3.groups().byGroupId(groupId).artifacts().post(createOrderXsd);

        // Now create the RAML artifact, with a reference to order.xsd
        String artifactId = TestUtils.generateArtifactId();
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, "RAML", RAML_CONTENT, ContentTypes.APPLICATION_YAML);
        createArtifact.getFirstVersion().setVersion("1.0");
        createArtifact.getFirstVersion().getContent().setReferences(List.of(
                artifactReference("schemas/order.xsd", groupId, "order-xsd", "1.0")
        ));
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        // Get the normal (unchanged) content of the RAML artifact
        InputStream stream = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("1.0")
                .content().get();
        String content = IoUtil.toString(stream);
        Assertions.assertNotNull(content);
        Assertions.assertEquals(RAML_CONTENT, content);

        // Get the dereferenced content of the RAML artifact
        stream = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("1.0")
                .content().get(config -> config.queryParameters.references = HandleReferencesType.DEREFERENCE);
        String dereferencedContent = IoUtil.toString(stream);
        Assertions.assertNotNull(dereferencedContent);
        Assertions.assertEquals(DEREFERENCED_RAML_CONTENT, dereferencedContent);

        // Get the rewritten content of the RAML artifact
        stream = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("1.0")
                .content().get(config -> config.queryParameters.references = HandleReferencesType.REWRITE);
        String rewrittenContent = IoUtil.toString(stream);
        Assertions.assertNotNull(rewrittenContent);
        // Note: Cannot test this easily because the rewritten content contains URLs based on the host and port
        //       of the test server, which will be dynamic.  Check for the static parts of the URL instead.
//        Assertions.assertEquals(DEREFERENCED_RAML_CONTENT, rewrittenContent);
        Assertions.assertTrue(rewrittenContent.contains(groupId));
        Assertions.assertTrue(rewrittenContent.contains("order-xsd"));
    }

    // OK: it turns out that ReferenceFinder is NOT USED on the server at the moment.  It's only used
    // by the maven plugin to implement the "autorefs" feature.  Perhaps in the future we'll use the
    // reference finder for something on the server, and then it can be tested.
//    @Test
//    public void testReferenceFinder() {
//        String groupId = TestUtils.generateGroupId();
//
//        // Create the group so we can enable the integrity rule
//        CreateGroup createGroup = new CreateGroup();
//        createGroup.setGroupId(groupId);
//        clientV3.groups().post(createGroup);
//
//        // Enable the integrity rule (all refs must be mapped)
//        CreateRule createRule = new CreateRule();
//        createRule.setRuleType(RuleType.INTEGRITY);
//        createRule.setConfig(IntegrityLevel.ALL_REFS_MAPPED.name());
//        clientV3.groups().byGroupId(groupId).rules().post(createRule);
//
//        // Now create the RAML artifact, with a reference to schemas/order.xsd
//        String artifactId = TestUtils.generateArtifactId();
//        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, "RAML", RAML_CONTENT, ContentTypes.APPLICATION_YAML);
//        createArtifact.getFirstVersion().setVersion("1.0");
//        createArtifact.getFirstVersion().getContent().setReferences(List.of(
//                artifactReference("schemas/order.xsd", groupId, "order-xsd", "1.0")
//        ));
//        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
//
//        // Now create the RAML artifact, WITHOUT a reference to schemas/order.xsd
//        Assertions.assertThrows(RuleViolationProblemDetails.class, () -> {
//            String artifactId_NoRefs = TestUtils.generateArtifactId();
//            CreateArtifact createArtifact_NoRefs = TestUtils.clientCreateArtifact(artifactId_NoRefs, "RAML", RAML_CONTENT, ContentTypes.APPLICATION_YAML);
//            createArtifact_NoRefs.getFirstVersion().setVersion("1.0");
//            clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact_NoRefs);
//        });
//    }

    private ArtifactReference artifactReference(String name, String groupId, String artifactId, String version) {
        ArtifactReference ref = new ArtifactReference();
        ref.setName(name);
        ref.setGroupId(groupId);
        ref.setArtifactId(artifactId);
        ref.setVersion(version);
        return ref;
    }

}