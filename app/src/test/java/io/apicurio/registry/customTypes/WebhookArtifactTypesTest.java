package io.apicurio.registry.customTypes;

import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.CreateArtifact;
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
import java.util.Map;

@QuarkusTest
@TestProfile(WebhookArtifactTypesTestProfile.class)
public class WebhookArtifactTypesTest extends AbstractCustomArtifactTypesTest {

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
    public void testCanonicalizerReceivesReferenceContentType() {
        String groupId = TestUtils.generateGroupId();
        String referenceName = "schemas/order.yaml";

        // Create the artifact that will be referenced, stored as YAML.  Its artifact type is "RAML"
        // (this profile disables the built-in types), which differs from its content type.
        CreateArtifact createReferenced = TestUtils.clientCreateArtifact("order-yaml", "RAML", "orderId: 1", ContentTypes.APPLICATION_YAML);
        createReferenced.getFirstVersion().setVersion("1.0");
        clientV3.groups().byGroupId(groupId).artifacts().post(createReferenced);

        // Create a RAML artifact with a reference to the YAML artifact.
        String artifactId = TestUtils.generateArtifactId();
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, "RAML", "#%RAML 1.0\ntitle: testCanonicalizerReceivesReferenceContentType", ContentTypes.APPLICATION_YAML);
        createArtifact.getFirstVersion().setVersion("1.0");
        ArtifactReference reference = new ArtifactReference();
        reference.setName(referenceName);
        reference.setGroupId(groupId);
        reference.setArtifactId("order-yaml");
        reference.setVersion("1.0");
        createArtifact.getFirstVersion().getContent().setReferences(List.of(reference));
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        try {
            // Creating the artifact also canonicalized it, so discard anything recorded so far.
            ramlMicroService.clearCanonicalizerReferenceContentTypes();

            // Fetching the canonical content canonicalizes it again, this time with the references
            // resolved by RegistryContentUtils.recursivelyResolveReferences.
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("1.0")
                    .content().get(config -> config.queryParameters.canonical = true);

            // The webhook must receive the reference with its content type, not its artifact type.
            Assertions.assertEquals(Map.of(referenceName, ContentTypes.APPLICATION_YAML),
                    ramlMicroService.getCanonicalizerReferenceContentTypes());
        } finally {
            ramlMicroService.clearCanonicalizerReferenceContentTypes();
        }
    }

}