package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.EnvironmentVariables;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.spec.FeaturesSpec;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.operator.api.v1.ContainerNames.REGISTRY_APP_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.getContainerFromDeployment;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class AppFeaturesITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(AppFeaturesITTest.class);

    @Test
    void testAllowDeletesTrue() {
        ApicurioRegistry3 registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        // Set Allow deletes = true
        registry.getSpec().getApp().setFeatures(FeaturesSpec.builder().allowDeletes(true).build());
        client.resource(registry).create();

        // Wait for the deployment to exist
        checkDeploymentExists(registry, ResourceFactory.COMPONENT_APP, 1);

        // Check that the three deletion ENV vars are set
        var appEnv = getContainerFromDeployment(
                client.apps().deployments().inNamespace(namespace).withName(registry.getMetadata().getName() + "-app-deployment").get(),
                REGISTRY_APP_CONTAINER_NAME).getEnv();
        assertThat(appEnv).map(EnvVar::getName).contains(
                EnvironmentVariables.APICURIO_REST_DELETION_ARTIFACT_ENABLED,
                EnvironmentVariables.APICURIO_REST_DELETION_ARTIFACT_VERSION_ENABLED,
                EnvironmentVariables.APICURIO_REST_DELETION_GROUP_ENABLED);
    }

    @Test
    void testAllowDeletesDefault() {
        ApicurioRegistry3 registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        client.resource(registry).create();

        // Wait for the deployment to exist
        checkDeploymentExists(registry, ResourceFactory.COMPONENT_APP, 1);

        // Check that the three deletion ENV vars are NOT set
        var appEnv = getContainerFromDeployment(
                client.apps().deployments().inNamespace(namespace).withName(registry.getMetadata().getName() + "-app-deployment").get(),
                REGISTRY_APP_CONTAINER_NAME).getEnv();
        assertThat(appEnv).map(EnvVar::getName).doesNotContain(
                EnvironmentVariables.APICURIO_REST_DELETION_ARTIFACT_ENABLED,
                EnvironmentVariables.APICURIO_REST_DELETION_ARTIFACT_VERSION_ENABLED,
                EnvironmentVariables.APICURIO_REST_DELETION_GROUP_ENABLED);
    }
}
