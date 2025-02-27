package io.apicurio.registry.operator.feat;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.AppSpec;
import io.apicurio.registry.operator.api.v1.spec.TLSSpec;
import io.apicurio.registry.operator.utils.SecretKeyRefTool;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.apps.Deployment;

import java.util.Map;
import java.util.Optional;

import static io.apicurio.registry.operator.EnvironmentVariables.*;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.addEnvVar;
import static java.util.Optional.ofNullable;

public class TLS {

    public static void configureTLS(ApicurioRegistry3 primary, Deployment deployment,
                               String containerName, Map<String, EnvVar> env) {

        addEnvVar(env, QUARKUS_HTTP_INSECURE_REQUESTS, Optional.ofNullable(primary.getSpec())
                .map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getTls)
                .map(TLSSpec::getInsecureRequests)
                .orElse("enabled"));

        var keystore = new SecretKeyRefTool(getTlsSpec(primary)
                .map(TLSSpec::getKeystoreSecretRef)
                .orElse(null), "user.p12");

        var keystorePassword = new SecretKeyRefTool(getTlsSpec(primary)
                .map(TLSSpec::getKeystorePasswordSecretRef)
                .orElse(null), "user.password");

        var truststore = new SecretKeyRefTool(getTlsSpec(primary)
                .map(TLSSpec::getTruststoreSecretRef)
                .orElse(null), "ca.p12");

        var truststorePassword = new SecretKeyRefTool(getTlsSpec(primary)
                .map(TLSSpec::getTruststorePasswordSecretRef)
                .orElse(null), "ca.password");

        if (truststore.isValid() && truststorePassword.isValid()) {
            // ===== Truststore
            truststore.applySecretVolume(deployment, containerName);
            addEnvVar(env, QUARKUS_TLS_TRUST_STORE_P12_PATH, truststore.getSecretVolumeKeyPath());
            truststorePassword.applySecretEnvVar(env, QUARKUS_TLS_TRUST_STORE_P12_PASSWORD);
        }

        if (keystore.isValid()
                && keystorePassword.isValid()) {
            // ===== Keystore
            keystore.applySecretVolume(deployment, containerName);
            addEnvVar(env, QUARKUS_TLS_KEY_STORE_P12_PATH, keystore.getSecretVolumeKeyPath());
            keystorePassword.applySecretEnvVar(env, QUARKUS_TLS_KEY_STORE_P12_PASSWORD);
        }
    }

    private static Optional<TLSSpec> getTlsSpec(ApicurioRegistry3 primary) {
        return ofNullable(primary)
                .map(ApicurioRegistry3::getSpec)
                .map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getTls);
    }
}
