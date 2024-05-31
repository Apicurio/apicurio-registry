package io.apicurio.registry.auth;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.BasicAuthWithStrimziUsersTestProfile;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonDeletingOperation;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static io.apicurio.registry.client.auth.VertXAuthFactory.buildSimpleAuthWebClient;

@QuarkusTest
@WithKubernetesTestServer
@TestProfile(BasicAuthWithStrimziUsersTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class StrimziAuthTest extends AbstractResourceTestBase {
    @Inject
    KubernetesClient client;

    @Inject
    AuthConfig authConfig;

    private static final String USERNAME = "alice";
    private static final String PASSWORD = "hunter2";
    private static final String BAD_PASSWORD = "asdf";

    @BeforeEach
    public void setupKubernetesResources() {
        NamespaceBuilder namespaceBuilder = new NamespaceBuilder().withNewMetadata().withName(authConfig.strimziKubernetesNamespace).endMetadata();
        client.namespaces().resource(namespaceBuilder.build()).createOr(NonDeletingOperation::update);
        // Create secret for user alice
        SecretBuilder secretBuilder = new SecretBuilder()
            .withNewMetadata()
            .withName(USERNAME)
            .withNamespace(authConfig.strimziKubernetesNamespace)
            .withLabels(Map.of("strimzi.io/kind", "KafkaUser"))
            .endMetadata()
            .addToData("password", Base64.getEncoder().encodeToString(PASSWORD.getBytes(StandardCharsets.UTF_8)));
        client.secrets().inNamespace(authConfig.strimziKubernetesNamespace).resource(secretBuilder.build()).createOr(NonDeletingOperation::update);
    }

    @Test
    public void testWrongCreds() throws Exception {
        var adapter = new VertXRequestAdapter(buildSimpleAuthWebClient(USERNAME, BAD_PASSWORD));
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);
        var exception = Assertions.assertThrows(Exception.class, () -> {
            client.groups().byGroupId("authTestGroupId").artifacts().get();
        });
        assertNotAuthorized(exception);
    }

    @Test
    public void testUserDoesNotExist() throws Exception {
        var adapter = new VertXRequestAdapter(buildSimpleAuthWebClient("bob", PASSWORD));
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);
        var exception = Assertions.assertThrows(Exception.class, () -> {
            client.groups().byGroupId("authTestGroupId").artifacts().get();
        });
        assertNotAuthorized(exception);
    }

    @Test
    public void testAuthOk() throws Exception {
        var adapter = new VertXRequestAdapter(buildSimpleAuthWebClient(USERNAME, PASSWORD));
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);
        Assertions.assertDoesNotThrow(() -> {
            client.groups().byGroupId("authTestGroupId").artifacts().get();
        });
    }

    @Override
    protected void deleteGlobalRules(int expectedDefaultRulesCount) throws Exception {
        // Do nothing
    }
}
