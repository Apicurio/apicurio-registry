package io.apicurio.registry.operator.unit;

import io.apicurio.registry.operator.EnvironmentVariables;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.feat.KubernetesOps;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.EnvVar;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

public class KubernetesOpsTest {

    @Test
    public void testBasicConfiguration() {
        var registry = deserialize("k8s/examples/kubernetesops/example-basic.yaml");
        var envVars = new LinkedHashMap<String, EnvVar>();

        KubernetesOps.configureKubernetesOps(registry, envVars);

        assertThat(envVars).containsKey(EnvironmentVariables.APICURIO_STORAGE_KIND);
        assertThat(envVars.get(EnvironmentVariables.APICURIO_STORAGE_KIND).getValue())
                .isEqualTo("kubernetesops");

        assertThat(envVars).containsKey(EnvironmentVariables.APICURIO_FEATURES_EXPERIMENTAL_ENABLED);
        assertThat(envVars.get(EnvironmentVariables.APICURIO_FEATURES_EXPERIMENTAL_ENABLED).getValue())
                .isEqualTo("true");

        assertThat(envVars).containsKey(EnvironmentVariables.APICURIO_KUBERNETESOPS_ID);
        assertThat(envVars.get(EnvironmentVariables.APICURIO_KUBERNETESOPS_ID).getValue())
                .isEqualTo("my-registry");

        assertThat(envVars).containsKey(EnvironmentVariables.APICURIO_KUBERNETESOPS_NAMESPACE);
        assertThat(envVars.get(EnvironmentVariables.APICURIO_KUBERNETESOPS_NAMESPACE).getValue())
                .isEqualTo("apicurio");
    }

    @Test
    public void testFullConfiguration() {
        var registry = deserialize("k8s/examples/kubernetesops/example-full.yaml");
        var envVars = new LinkedHashMap<String, EnvVar>();

        KubernetesOps.configureKubernetesOps(registry, envVars);

        assertThat(envVars.get(EnvironmentVariables.APICURIO_STORAGE_KIND).getValue())
                .isEqualTo("kubernetesops");
        assertThat(envVars.get(EnvironmentVariables.APICURIO_FEATURES_EXPERIMENTAL_ENABLED).getValue())
                .isEqualTo("true");
        assertThat(envVars.get(EnvironmentVariables.APICURIO_KUBERNETESOPS_ID).getValue())
                .isEqualTo("my-registry");
        assertThat(envVars.get(EnvironmentVariables.APICURIO_KUBERNETESOPS_NAMESPACE).getValue())
                .isEqualTo("apicurio");
        assertThat(envVars.get(EnvironmentVariables.APICURIO_KUBERNETESOPS_REFRESH_EVERY).getValue())
                .isEqualTo("10s");
        assertThat(envVars.get(EnvironmentVariables.APICURIO_KUBERNETESOPS_LABEL_REGISTRY_ID).getValue())
                .isEqualTo("custom.label/registry");
        assertThat(envVars.get(EnvironmentVariables.APICURIO_KUBERNETESOPS_WATCH_ENABLED).getValue())
                .isEqualTo("false");
        assertThat(envVars.get(EnvironmentVariables.APICURIO_KUBERNETESOPS_WATCH_RECONNECT_DELAY).getValue())
                .isEqualTo("5s");
    }

    @Test
    public void testMinimalConfiguration() {
        var registry = deserialize("k8s/examples/kubernetesops/example-minimal.yaml");
        var envVars = new LinkedHashMap<String, EnvVar>();

        KubernetesOps.configureKubernetesOps(registry, envVars);

        assertThat(envVars.get(EnvironmentVariables.APICURIO_STORAGE_KIND).getValue())
                .isEqualTo("kubernetesops");
        assertThat(envVars.get(EnvironmentVariables.APICURIO_FEATURES_EXPERIMENTAL_ENABLED).getValue())
                .isEqualTo("true");
        assertThat(envVars.get(EnvironmentVariables.APICURIO_KUBERNETESOPS_ID).getValue())
                .isEqualTo("my-registry");

        // Optional fields should not be set
        assertThat(envVars).doesNotContainKey(EnvironmentVariables.APICURIO_KUBERNETESOPS_NAMESPACE);
        assertThat(envVars).doesNotContainKey(EnvironmentVariables.APICURIO_KUBERNETESOPS_REFRESH_EVERY);
        assertThat(envVars).doesNotContainKey(EnvironmentVariables.APICURIO_KUBERNETESOPS_LABEL_REGISTRY_ID);
        assertThat(envVars).doesNotContainKey(EnvironmentVariables.APICURIO_KUBERNETESOPS_WATCH_ENABLED);
        assertThat(envVars).doesNotContainKey(EnvironmentVariables.APICURIO_KUBERNETESOPS_WATCH_RECONNECT_DELAY);
    }

    @Test
    public void testNoKubernetesOpsSpec() {
        var registry = deserialize("k8s/examples/simple.apicurioregistry3.yaml");
        var envVars = new LinkedHashMap<String, EnvVar>();

        KubernetesOps.configureKubernetesOps(registry, envVars);

        // No env vars should be set for a non-kubernetesops registry
        assertThat(envVars).isEmpty();
    }

    private ApicurioRegistry3 deserialize(String path) {
        return ResourceFactory.deserialize(path, ApicurioRegistry3.class,
                KubernetesOpsTest.class.getClassLoader());
    }
}
