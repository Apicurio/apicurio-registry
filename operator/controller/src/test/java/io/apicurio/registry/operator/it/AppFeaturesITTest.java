package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.EnvironmentVariables;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.spec.AppFeaturesSpec;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.operator.api.v1.ContainerNames.REGISTRY_APP_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.getContainerFromDeployment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
public class AppFeaturesITTest extends ITBase {

    @Test
    void testAllowDeletesTrue() {
        ApicurioRegistry3 registry = ResourceFactory
                .deserialize("/k8s/examples/simple.apicurioregistry3.yaml", ApicurioRegistry3.class);
        // Set Allow deletes = true
        registry.getSpec().getApp().setFeatures(AppFeaturesSpec.builder().resourceDeleteEnabled(true).build());
        client.resource(registry).create();

        // Wait for the deployment to exist
        checkDeploymentExists(registry, ResourceFactory.COMPONENT_APP, 1);

        // Check that the three deletion ENV vars are set
        await().untilAsserted(() -> {
            var appEnv = getContainerFromDeployment(
                    client.apps().deployments().inNamespace(namespace)
                            .withName(registry.getMetadata().getName() + "-app-deployment").get(),
                    REGISTRY_APP_CONTAINER_NAME).getEnv();
            assertThat(appEnv).map(EnvVar::getName).contains(
                    EnvironmentVariables.APICURIO_REST_DELETION_ARTIFACT_ENABLED,
                    EnvironmentVariables.APICURIO_REST_DELETION_ARTIFACT_VERSION_ENABLED,
                    EnvironmentVariables.APICURIO_REST_DELETION_GROUP_ENABLED);
        });
    }

    @Test
    void testAllowDeletesDefault() {
        ApicurioRegistry3 registry = ResourceFactory
                .deserialize("/k8s/examples/simple.apicurioregistry3.yaml", ApicurioRegistry3.class);
        client.resource(registry).create();

        // Wait for the deployment to exist
        checkDeploymentExists(registry, ResourceFactory.COMPONENT_APP, 1);

        // Check that the three deletion ENV vars are NOT set
        await().untilAsserted(() -> {
            var appEnv = getContainerFromDeployment(
                    client.apps().deployments().inNamespace(namespace)
                            .withName(registry.getMetadata().getName() + "-app-deployment").get(),
                    REGISTRY_APP_CONTAINER_NAME).getEnv();
            assertThat(appEnv).map(EnvVar::getName).doesNotContain(
                    EnvironmentVariables.APICURIO_REST_DELETION_ARTIFACT_ENABLED,
                    EnvironmentVariables.APICURIO_REST_DELETION_ARTIFACT_VERSION_ENABLED,
                    EnvironmentVariables.APICURIO_REST_DELETION_GROUP_ENABLED);
        });
    }

    @Test
    void testVersionMutabilityEnabledTrue() {
        ApicurioRegistry3 registry = ResourceFactory
                .deserialize("/k8s/examples/simple.apicurioregistry3.yaml", ApicurioRegistry3.class);
        // Set version mutability enabled = true
        registry.getSpec().getApp().setFeatures(AppFeaturesSpec.builder().versionMutabilityEnabled(true).build());
        client.resource(registry).create();

        // Wait for the deployment to exist
        checkDeploymentExists(registry, ResourceFactory.COMPONENT_APP, 1);

        // Check that the mutability ENV var is set with value "true"
        await().untilAsserted(() -> {
            var appEnv = getContainerFromDeployment(
                    client.apps().deployments().inNamespace(namespace)
                            .withName(registry.getMetadata().getName() + "-app-deployment").get(),
                    REGISTRY_APP_CONTAINER_NAME).getEnv();
            assertThat(appEnv)
                    .filteredOn(e -> e.getName().equals(EnvironmentVariables.APICURIO_REST_MUTABILITY_ARTIFACT_VERSION_CONTENT_ENABLED))
                    .hasSize(1)
                    .first()
                    .extracting(EnvVar::getValue)
                    .isEqualTo("true");
        });
    }

    @Test
    void testVersionMutabilityEnabledDefault() {
        ApicurioRegistry3 registry = ResourceFactory
                .deserialize("/k8s/examples/simple.apicurioregistry3.yaml", ApicurioRegistry3.class);
        client.resource(registry).create();

        // Wait for the deployment to exist
        checkDeploymentExists(registry, ResourceFactory.COMPONENT_APP, 1);

        // Check that the mutability ENV var is NOT set
        await().untilAsserted(() -> {
            var appEnv = getContainerFromDeployment(
                    client.apps().deployments().inNamespace(namespace)
                            .withName(registry.getMetadata().getName() + "-app-deployment").get(),
                    REGISTRY_APP_CONTAINER_NAME).getEnv();
            assertThat(appEnv).map(EnvVar::getName).doesNotContain(
                    EnvironmentVariables.APICURIO_REST_MUTABILITY_ARTIFACT_VERSION_CONTENT_ENABLED);
        });
    }
}
