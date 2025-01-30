package io.apicurio.registry.operator.feat;

import io.apicurio.registry.operator.api.v1.spec.auth.AppAuthSpec;
import io.apicurio.registry.operator.api.v1.spec.auth.AuthTLSSpec;
import io.apicurio.registry.operator.utils.SecretKeyRefTool;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.apps.Deployment;

import java.util.Map;
import java.util.Optional;

import static io.apicurio.registry.operator.EnvironmentVariables.*;
import static io.apicurio.registry.operator.api.v1.ContainerNames.REGISTRY_APP_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.addEnvVar;
import static java.util.Optional.ofNullable;

public class AuthTLS {

    /**
     * Configure TLS for OIDC authentication
     */
    public static boolean configureAuthTLS(AppAuthSpec appAuthSpec, Deployment deployment,
            Map<String, EnvVar> env) {

        // spotless:off
        var keystore = new SecretKeyRefTool(getAuthTLSSpec(appAuthSpec)
                .map(AuthTLSSpec::getKeystoreSecretRef)
                .orElse(null), "user.p12");

        var keystorePassword = new SecretKeyRefTool(getAuthTLSSpec(appAuthSpec)
                .map(AuthTLSSpec::getKeystorePasswordSecretRef)
                .orElse(null), "user.password");

        var truststore = new SecretKeyRefTool(getAuthTLSSpec(appAuthSpec)
                .map(AuthTLSSpec::getTruststoreSecretRef)
                .orElse(null), "ca.p12");

        var truststorePassword = new SecretKeyRefTool(getAuthTLSSpec(appAuthSpec)
                .map(AuthTLSSpec::getTruststorePasswordSecretRef)
                .orElse(null), "ca.password");
        // spotless:on

        if (truststore.isValid() && truststorePassword.isValid() && keystore.isValid()
                && keystorePassword.isValid()) {

            // ===== Keystore

            addEnvVar(env, OIDC_TLS_KEYSTORE_TYPE, "PKCS12");
            keystore.applySecretVolume(deployment, REGISTRY_APP_CONTAINER_NAME);
            addEnvVar(env, OIDC_TLS_KEYSTORE_LOCATION, keystore.getSecretVolumeKeyPath());
            keystorePassword.applySecretEnvVar(env, OIDC_TLS_KEYSTORE_PASSWORD);

            // ===== Truststore

            addEnvVar(env, OIDC_TLS_TRUSTSTORE_TYPE, "PKCS12");
            truststore.applySecretVolume(deployment, REGISTRY_APP_CONTAINER_NAME);
            addEnvVar(env, OIDC_TLS_TRUSTSTORE_LOCATION, truststore.getSecretVolumeKeyPath());
            truststorePassword.applySecretEnvVar(env, OIDC_TLS_TRUSTSTORE_PASSWORD);

            return true;
        }
        return false;
    }

    private static Optional<AuthTLSSpec> getAuthTLSSpec(AppAuthSpec primary) {
        // spotless:off
        return ofNullable(primary)
                .map(AppAuthSpec::getTls);
        // spotless:on
    }
}
