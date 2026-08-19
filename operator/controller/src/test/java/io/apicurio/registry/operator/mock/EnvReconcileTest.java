package io.apicurio.registry.operator.mock;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.apicurio.registry.operator.api.v1.ContainerNames.REGISTRY_APP_CONTAINER_NAME;
import static io.apicurio.registry.operator.api.v1.ContainerNames.REGISTRY_UI_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.getContainerFromDeployment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Mock-server equivalent of {@code EnvITTest}: verifies env var propagation and ordering.
 */
@QuarkusTest
public class EnvReconcileTest extends MockServerTestBase {

    private static final String[] DEFAULT_APP_ENV = {
            "QUARKUS_PROFILE",
            "QUARKUS_HTTP_ACCESS_LOG_ENABLED",
            "QUARKUS_HTTP_CORS_ORIGINS"
    };

    private static final String[] DEFAULT_UI_ENV = {"REGISTRY_API_URL"};

    @Test
    void envVars() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getSpec().getApp().getIngress().setHost("app.example.com");
        registry.getSpec().getUi().getIngress().setHost("ui.example.com");
        createRegistry(registry);

        // Default env vars are set exactly once
        await().atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions().untilAsserted(() -> {
            var appEnv = getContainerFromDeployment(
                    client.apps().deployments().inNamespace(namespace)
                            .withName(deploymentName(registry, COMPONENT_APP)).get(),
                    REGISTRY_APP_CONTAINER_NAME).getEnv();
            assertThat(appEnv).map(EnvVar::getName).containsOnlyOnce(DEFAULT_APP_ENV);

            var uiEnv = getContainerFromDeployment(
                    client.apps().deployments().inNamespace(namespace)
                            .withName(deploymentName(registry, COMPONENT_UI)).get(),
                    REGISTRY_UI_CONTAINER_NAME).getEnv();
            assertThat(uiEnv).map(EnvVar::getName).containsOnlyOnce(DEFAULT_UI_ENV);
        });

        // Add custom env vars and override a default one
        updateRegistry(registry, r -> {
            r.getSpec().getApp().setEnv(List.of(
                    new EnvVarBuilder().withName("APP_VAR_1_NAME").withValue("APP_VAR_1_VALUE").build(),
                    new EnvVarBuilder().withName("QUARKUS_HTTP_ACCESS_LOG_ENABLED").withValue("false").build(),
                    new EnvVarBuilder().withName("APP_VAR_2_NAME").withValue("APP_VAR_2_VALUE").build()
            ));
            r.getSpec().getUi().setEnv(List.of(
                    new EnvVarBuilder().withName("UI_VAR_1_NAME").withValue("UI_VAR_1_VALUE").build(),
                    new EnvVarBuilder().withName("REGISTRY_API_URL").withValue("FOO").build(),
                    new EnvVarBuilder().withName("UI_VAR_2_NAME").withValue("UI_VAR_2_VALUE").build()
            ));
        });

        await().atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions().untilAsserted(() -> {
            var appEnv = getContainerFromDeployment(
                    client.apps().deployments().inNamespace(namespace)
                            .withName(deploymentName(registry, COMPONENT_APP)).get(),
                    REGISTRY_APP_CONTAINER_NAME).getEnv();
            assertThat(appEnv).map(EnvVar::getName).containsOnlyOnce(DEFAULT_APP_ENV);
            assertThat(appEnv.stream().filter(e -> "QUARKUS_HTTP_ACCESS_LOG_ENABLED".equals(e.getName()))
                    .map(EnvVar::getValue).findAny()).hasValue("false");
            assertThat(appEnv).containsSubsequence(
                    new EnvVarBuilder().withName("APP_VAR_1_NAME").withValue("APP_VAR_1_VALUE").build(),
                    new EnvVarBuilder().withName("APP_VAR_2_NAME").withValue("APP_VAR_2_VALUE").build()
            );

            var uiEnv = getContainerFromDeployment(
                    client.apps().deployments().inNamespace(namespace)
                            .withName(deploymentName(registry, COMPONENT_UI)).get(),
                    REGISTRY_UI_CONTAINER_NAME).getEnv();
            assertThat(uiEnv).map(EnvVar::getName).containsOnlyOnce(DEFAULT_UI_ENV);
            assertThat(uiEnv.stream().filter(e -> "REGISTRY_API_URL".equals(e.getName()))
                    .map(EnvVar::getValue).findAny()).hasValue("FOO");
            assertThat(uiEnv).containsSubsequence(
                    new EnvVarBuilder().withName("UI_VAR_1_NAME").withValue("UI_VAR_1_VALUE").build(),
                    new EnvVarBuilder().withName("UI_VAR_2_NAME").withValue("UI_VAR_2_VALUE").build()
            );
        });

        // Change order: verify ordering is respected
        updateRegistry(registry, r -> {
            r.getSpec().getApp().setEnv(List.of(
                    new EnvVarBuilder().withName("APP_VAR_2_NAME").withValue("APP_VAR_2_VALUE").build(),
                    new EnvVarBuilder().withName("QUARKUS_HTTP_ACCESS_LOG_ENABLED").withValue("false").build(),
                    new EnvVarBuilder().withName("APP_VAR_1_NAME").withValue("APP_VAR_1_VALUE").build()
            ));
            r.getSpec().getUi().setEnv(List.of(
                    new EnvVarBuilder().withName("UI_VAR_2_NAME").withValue("UI_VAR_2_VALUE").build(),
                    new EnvVarBuilder().withName("REGISTRY_API_URL").withValue("FOO").build(),
                    new EnvVarBuilder().withName("UI_VAR_1_NAME").withValue("UI_VAR_1_VALUE").build()
            ));
        });

        await().atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions().untilAsserted(() -> {
            var appEnv = getContainerFromDeployment(
                    client.apps().deployments().inNamespace(namespace)
                            .withName(deploymentName(registry, COMPONENT_APP)).get(),
                    REGISTRY_APP_CONTAINER_NAME).getEnv();
            assertThat(appEnv).containsSubsequence(
                    new EnvVarBuilder().withName("APP_VAR_2_NAME").withValue("APP_VAR_2_VALUE").build(),
                    new EnvVarBuilder().withName("APP_VAR_1_NAME").withValue("APP_VAR_1_VALUE").build()
            );

            var uiEnv = getContainerFromDeployment(
                    client.apps().deployments().inNamespace(namespace)
                            .withName(deploymentName(registry, COMPONENT_UI)).get(),
                    REGISTRY_UI_CONTAINER_NAME).getEnv();
            assertThat(uiEnv).containsSubsequence(
                    new EnvVarBuilder().withName("UI_VAR_2_NAME").withValue("UI_VAR_2_VALUE").build(),
                    new EnvVarBuilder().withName("UI_VAR_1_NAME").withValue("UI_VAR_1_VALUE").build()
            );
        });
    }
}
