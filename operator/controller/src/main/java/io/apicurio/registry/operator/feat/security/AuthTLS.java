package io.apicurio.registry.operator.feat.security;

import io.apicurio.registry.operator.EnvironmentVariables;
import io.apicurio.registry.operator.api.v1.spec.auth.AuthSpec;
import io.apicurio.registry.operator.api.v1.spec.auth.AuthTLSSpec;
import io.apicurio.registry.operator.utils.SecretKeyRefTool;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.apps.Deployment;

import java.util.Map;
import java.util.Optional;

import static io.apicurio.registry.operator.EnvironmentVariables.*;
import static io.apicurio.registry.operator.api.v1.ContainerNames.REGISTRY_APP_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.addEnvVar;
import static io.apicurio.registry.operator.utils.Utils.putIfNotBlank;
import static java.util.Optional.ofNullable;

public class AuthTLS {

    /**
     * Configure TLS for OIDC authentication
     */
    public static void configureAuthTLS(AuthSpec authSpec, Deployment deployment, Map<String, EnvVar> env) {

        putIfNotBlank(env, EnvironmentVariables.OIDC_TLS_VERIFICATION,
                authSpec.getTls().getTlsVerificationType());

        // spotless:off
        var truststore = new SecretKeyRefTool(getAuthTLSSpec(authSpec)
                .map(AuthTLSSpec::getTruststoreSecretRef)
                .orElse(null), "ca.p12");

        var truststorePassword = new SecretKeyRefTool(getAuthTLSSpec(authSpec)
                .map(AuthTLSSpec::getTruststorePasswordSecretRef)
                .orElse(null), "ca.password");
        // spotless:on
        if (truststore.isValid() && truststorePassword.isValid()) {
            truststore.applySecretVolume(deployment, REGISTRY_APP_CONTAINER_NAME);
            addEnvVar(env, OIDC_TLS_TRUSTSTORE_LOCATION, truststore.getSecretVolumeKeyPath());
            truststorePassword.applySecretEnvVar(env, OIDC_TLS_TRUSTSTORE_PASSWORD);
        }
    }

    private static Optional<AuthTLSSpec> getAuthTLSSpec(AuthSpec primary) {
        // spotless:off
        return ofNullable(primary)
                .map(AuthSpec::getTls);
        // spotless:on
    }
}
