package io.apicurio.registry.customTypes;

import io.apicurio.utils.test.raml.microsvc.RamlTestMicroService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

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

}