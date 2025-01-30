package io.apicurio.registry.operator.feat;

import io.apicurio.registry.operator.EnvironmentVariables;
import io.apicurio.registry.operator.api.v1.spec.auth.AppAuthSpec;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.apps.Deployment;

import java.util.Map;
import java.util.Optional;

import static io.apicurio.registry.operator.utils.Utils.createEnvVar;
import static io.apicurio.registry.operator.utils.Utils.putIfNotBlank;

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

        AuthTLS.configureAuthTLS(appAuthSpec, deployment, env);
    }
}
