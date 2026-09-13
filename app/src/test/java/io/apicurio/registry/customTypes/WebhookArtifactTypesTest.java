package io.apicurio.registry.customTypes;

import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.apicurio.registry.rules.validity.ValidityLevel;
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

import java.util.concurrent.TimeUnit;

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
    public void testWebhookValidatorFailsClosed() throws Exception {
        String groupId = TestUtils.generateGroupId();

        // Create the group and enable the FULL validity rule.
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig(ValidityLevel.FULL.name());
        clientV3.groups().byGroupId(groupId).rules().post(createRule);

        // Stop the webhook server to simulate a network failure.
        ramlMicroService.stopServer();

        try {
            // Uploading content while the webhook is down must be rejected (fail-closed),
            // not silently accepted (fail-open).  Assert on the exception message to pin
            // this to the new code path: if the throw came from any other validator the
            // message would not contain the webhook-specific string, and the test would fail.
            RuleViolationProblemDetails ex = Assertions.assertThrows(RuleViolationProblemDetails.class, () -> {
                String artifactId = TestUtils.generateArtifactId();
                CreateArtifact createArtifact = TestUtils.clientCreateArtifact(
                        artifactId, "RAML", "#%RAML 1.0\ntitle: test", ContentTypes.APPLICATION_YAML);
                createArtifact.getFirstVersion().setVersion("1.0");
                clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
            });
            Assertions.assertTrue(
                    ex.getDetail() != null && ex.getDetail().contains("Webhook validation failed to execute"),
                    "Expected exception message to identify webhook failure, but got: " + ex.getDetail());
        } finally {
            // Restart the server so subsequent tests are unaffected.
            // Block on the deployment future (with a timeout) so the server is
            // guaranteed to be listening before the next test starts, and so that
            // a bind failure on port 3333 (e.g. still in TIME_WAIT) is surfaced
            // rather than silently swallowed.
            ramlMicroService = new RamlTestMicroService(3333);
            vertx.deployVerticle(ramlMicroService)
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }
    }

}
