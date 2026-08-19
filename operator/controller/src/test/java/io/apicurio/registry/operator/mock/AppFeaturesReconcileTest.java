package io.apicurio.registry.operator.mock;

import io.apicurio.registry.operator.EnvironmentVariables;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.spec.AppFeaturesSpec;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.operator.api.v1.ContainerNames.REGISTRY_APP_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.getContainerFromDeployment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Mock-server equivalents of {@code AppFeaturesITTest}.
 * All tests verify only env var presence/absence on the Deployment spec.
 */
@QuarkusTest
public class AppFeaturesReconcileTest extends MockServerTestBase {

    @Test
    void allowDeletesTrue() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getSpec().getApp().setFeatures(AppFeaturesSpec.builder().resourceDeleteEnabled(true).build());
        createRegistry(registry);

        awaitDeploymentExists(deploymentName(registry, COMPONENT_APP));

        await().atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions().untilAsserted(() -> {
            var env = getContainerFromDeployment(
                    client.apps().deployments().inNamespace(namespace)
                            .withName(deploymentName(registry, COMPONENT_APP)).get(),
                    REGISTRY_APP_CONTAINER_NAME).getEnv();
            assertThat(env).map(EnvVar::getName).contains(
                    EnvironmentVariables.APICURIO_REST_DELETION_ARTIFACT_ENABLED,
                    EnvironmentVariables.APICURIO_REST_DELETION_ARTIFACT_VERSION_ENABLED,
                    EnvironmentVariables.APICURIO_REST_DELETION_GROUP_ENABLED);
        });
    }

    @Test
    void allowDeletesDefault() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        createRegistry(registry);

        awaitDeploymentExists(deploymentName(registry, COMPONENT_APP));

        await().atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions().untilAsserted(() -> {
            var env = getContainerFromDeployment(
                    client.apps().deployments().inNamespace(namespace)
                            .withName(deploymentName(registry, COMPONENT_APP)).get(),
                    REGISTRY_APP_CONTAINER_NAME).getEnv();
            assertThat(env).map(EnvVar::getName).doesNotContain(
                    EnvironmentVariables.APICURIO_REST_DELETION_ARTIFACT_ENABLED,
                    EnvironmentVariables.APICURIO_REST_DELETION_ARTIFACT_VERSION_ENABLED,
                    EnvironmentVariables.APICURIO_REST_DELETION_GROUP_ENABLED);
        });
    }

    @Test
    void versionMutabilityEnabledTrue() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getSpec().getApp().setFeatures(AppFeaturesSpec.builder().versionMutabilityEnabled(true).build());
        createRegistry(registry);

        awaitDeploymentExists(deploymentName(registry, COMPONENT_APP));

        await().atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions().untilAsserted(() -> {
            var env = getContainerFromDeployment(
                    client.apps().deployments().inNamespace(namespace)
                            .withName(deploymentName(registry, COMPONENT_APP)).get(),
                    REGISTRY_APP_CONTAINER_NAME).getEnv();
            assertThat(env)
                    .filteredOn(e -> e.getName().equals(
                            EnvironmentVariables.APICURIO_REST_MUTABILITY_ARTIFACT_VERSION_CONTENT_ENABLED))
                    .hasSize(1)
                    .first()
                    .extracting(EnvVar::getValue)
                    .isEqualTo("true");
        });
    }

    @Test
    void versionMutabilityEnabledDefault() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        createRegistry(registry);

        awaitDeploymentExists(deploymentName(registry, COMPONENT_APP));

        await().atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions().untilAsserted(() -> {
            var env = getContainerFromDeployment(
                    client.apps().deployments().inNamespace(namespace)
                            .withName(deploymentName(registry, COMPONENT_APP)).get(),
                    REGISTRY_APP_CONTAINER_NAME).getEnv();
            assertThat(env).map(EnvVar::getName)
                    .doesNotContain(EnvironmentVariables.APICURIO_REST_MUTABILITY_ARTIFACT_VERSION_CONTENT_ENABLED);
        });
    }
}
