package io.apicurio.registry.operator.unit;

import io.apicurio.registry.operator.EnvironmentVariables;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.feat.security.KubernetesAuth;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.EnvVar;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

public class KubernetesAuthTest {

    private static final ClassLoader CLASS_LOADER = KubernetesAuthTest.class.getClassLoader();

    @Test
    public void testExperimentalFeatureGate() {
        var registry = deserialize("k8s/examples/auth/kubernetes-auth.apicurioregistry3.yaml");
        var envVars = new LinkedHashMap<String, EnvVar>();

        KubernetesAuth.configureKubernetesAuth(
                registry.getSpec().getApp().getAuth().getKubernetesAuth(), envVars);

        assertThat(envVars.get(EnvironmentVariables.APICURIO_AUTHN_KUBERNETES_ENABLED).getValue())
                .isEqualTo("true");

        assertThat(envVars.get(EnvironmentVariables.APICURIO_FEATURES_EXPERIMENTAL_ENABLED).getValue())
                .isEqualTo("true");
    }

    private ApicurioRegistry3 deserialize(String path) {
        return ResourceFactory.deserialize(path, ApicurioRegistry3.class, CLASS_LOADER);
    }
}
