package io.apicurio.registry.operator.feat;

import io.apicurio.registry.operator.EnvironmentVariables;
import io.apicurio.registry.operator.api.v1.spec.auth.AppAuthSpec;
import io.apicurio.registry.operator.utils.Utils;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;

import java.util.Map;
import java.util.Optional;

/**
 * Helper class used to handle AUTH related configuration.
 */
public class Auth {

    /**
     * Configures authentication-related environment variables for the Apicurio Registry.
     *
     * @param env The map of environment variables to be configured.
     * @param appAuthSpec The authentication specification containing required auth settings. If null, no
     *            changes will be made to envVars.
     */
    public static void configureAuth(AppAuthSpec appAuthSpec, Deployment deployment,
            Map<String, EnvVar> env) {
        if (appAuthSpec == null) {
            return;
        }

        env.put(EnvironmentVariables.APICURIO_REGISTRY_AUTH_ENABLED,
                createEnvVar(EnvironmentVariables.APICURIO_REGISTRY_AUTH_ENABLED,
                        Optional.ofNullable(appAuthSpec.getEnabled()).orElse(Boolean.FALSE).toString()));

        putIfNotBlank(env, EnvironmentVariables.APICURIO_REGISTRY_APP_CLIENT_ID,
                appAuthSpec.getAppClientId());
        putIfNotBlank(env, EnvironmentVariables.APICURIO_REGISTRY_UI_CLIENT_ID, appAuthSpec.getUiClientId());
        putIfNotBlank(env, EnvironmentVariables.APICURIO_UI_AUTH_OIDC_REDIRECT_URI,
                appAuthSpec.getRedirectURI());
        putIfNotBlank(env, EnvironmentVariables.APICURIO_UI_AUTH_OIDC_LOGOUT_URL, appAuthSpec.getLogoutURL());
        putIfNotBlank(env, EnvironmentVariables.APICURIO_REGISTRY_AUTH_SERVER_URL,
                appAuthSpec.getAuthServerUrl());
        putIfNotBlank(env, EnvironmentVariables.OIDC_TLS_VERIFICATION, appAuthSpec.getTlsVerification());

        AuthTLS.configureAuthTLS(appAuthSpec, deployment, env);
    }

    /**
     * Adds an environment variable to the map only if the value is not null or blank.
     *
     * @param envVars The environment variables map.
     * @param name The name of the environment variable.
     * @param value The value to be set (ignored if null or blank).
     */
    private static void putIfNotBlank(Map<String, EnvVar> envVars, String name, String value) {
        if (!Utils.isBlank(value)) {
            envVars.put(name, createEnvVar(name, value));
        }
    }

    /**
     * Creates an environment variable using the given name and value.
     *
     * @param name The name of the environment variable.
     * @param value The value of the environment variable.
     * @return An {@link EnvVar} instance with the specified name and value.
     */
    private static EnvVar createEnvVar(String name, String value) {
        return new EnvVarBuilder().withName(name).withValue(value).build();
    }
}
