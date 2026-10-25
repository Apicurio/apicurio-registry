package io.apicurio.registry.operator.unit;

import io.apicurio.registry.operator.EnvironmentVariables;
import io.apicurio.registry.operator.api.v1.spec.auth.KubernetesAuthSpec;
import io.apicurio.registry.operator.feat.security.KubernetesAuth;
import io.fabric8.kubernetes.api.model.EnvVar;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static io.apicurio.registry.operator.utils.Utils.createEnvVar;
import static org.assertj.core.api.Assertions.assertThat;

public class KubernetesAuthTest {

    @Test
    void testEnabledSetsExperimentalFeaturesGate() {
        var spec = KubernetesAuthSpec.builder().enabled(true).build();
        var envVars = new LinkedHashMap<String, EnvVar>();

        KubernetesAuth.configureKubernetesAuth(spec, envVars);

        assertThat(envVars.get(EnvironmentVariables.APICURIO_AUTHN_KUBERNETES_ENABLED).getValue())
                .isEqualTo("true");
        assertThat(envVars).containsKey(EnvironmentVariables.APICURIO_FEATURES_EXPERIMENTAL_ENABLED);
        assertThat(envVars.get(EnvironmentVariables.APICURIO_FEATURES_EXPERIMENTAL_ENABLED).getValue())
                .isEqualTo("true");
    }

    @Test
    void testEnabledKeepsUserProvidedExperimentalFeaturesGate() {
        var spec = KubernetesAuthSpec.builder().enabled(true).build();
        var envVars = new LinkedHashMap<String, EnvVar>();
        envVars.put(EnvironmentVariables.APICURIO_FEATURES_EXPERIMENTAL_ENABLED,
                createEnvVar(EnvironmentVariables.APICURIO_FEATURES_EXPERIMENTAL_ENABLED, "false"));

        KubernetesAuth.configureKubernetesAuth(spec, envVars);

        assertThat(envVars.get(EnvironmentVariables.APICURIO_FEATURES_EXPERIMENTAL_ENABLED).getValue())
                .isEqualTo("false");
    }

    @Test
    void testDisabledSetsNothing() {
        var spec = KubernetesAuthSpec.builder().enabled(false).build();
        var envVars = new LinkedHashMap<String, EnvVar>();

        KubernetesAuth.configureKubernetesAuth(spec, envVars);

        assertThat(envVars).isEmpty();
    }
}
