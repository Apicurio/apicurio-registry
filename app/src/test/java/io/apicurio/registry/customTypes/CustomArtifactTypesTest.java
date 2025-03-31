package io.apicurio.registry.customTypes;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactTypeInfo;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.utils.test.raml.microsvc.RamlTestMicroService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@QuarkusTest
@TestProfile(CustomArtifactTypesTestProfile.class)
public class CustomArtifactTypesTest extends AbstractResourceTestBase {

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

    private static Vertx vertx;
    private static RamlTestMicroService ramlMicroService;

    @BeforeAll
    public static void setup() {
        // Start the RAML microservice.  All RAML webhooks will call this service.  Must be running
        // or all of the test RAML webhooks will fail.
        vertx = Vertx.vertx();
        ramlMicroService = new RamlTestMicroService(3333);
        vertx.deployVerticle(ramlMicroService);
    }

    @AfterAll
    public static void cleanup() {
        // Shut down the RAML microservice.
        ramlMicroService.stopServer();
        vertx.close();
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
    public void testDiscoverRAMLArtifact() {
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

}