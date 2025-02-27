package io.apicurio.registry.operator.feat.security;

import io.apicurio.registry.operator.EnvironmentVariables;
import io.apicurio.registry.operator.api.v1.spec.auth.AuthSpec;
import io.fabric8.kubernetes.api.model.EnvVar;

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
     * @param authSpec The authentication specification containing required auth settings. If null, no changes
     *            will be made to envVars.
     */
    public static void configureAuth(AuthSpec authSpec, Map<String, EnvVar> env) {
        if (authSpec == null) {
            return;
        }

        env.put(EnvironmentVariables.APICURIO_REGISTRY_AUTH_ENABLED,
                createEnvVar(EnvironmentVariables.APICURIO_REGISTRY_AUTH_ENABLED,
                        Optional.ofNullable(authSpec.getEnabled()).orElse(Boolean.FALSE).toString()));

        putIfNotBlank(env, EnvironmentVariables.APICURIO_REGISTRY_APP_CLIENT_ID, authSpec.getAppClientId());
        putIfNotBlank(env, EnvironmentVariables.APICURIO_REGISTRY_UI_CLIENT_ID, authSpec.getUiClientId());
        putIfNotBlank(env, EnvironmentVariables.APICURIO_UI_AUTH_OIDC_REDIRECT_URI,
                authSpec.getRedirectURI());
        putIfNotBlank(env, EnvironmentVariables.APICURIO_UI_AUTH_OIDC_LOGOUT_URL, authSpec.getLogoutURL());
        putIfNotBlank(env, EnvironmentVariables.APICURIO_REGISTRY_AUTH_SERVER_URL,
                authSpec.getAuthServerUrl());

        if (authSpec.getAnonymousReads() != null && authSpec.getAnonymousReads()) {
            putIfNotBlank(env, EnvironmentVariables.APICURIO_AUTH_ANONYMOUS_READ_ACCESS_ENABLED,
                    authSpec.getAnonymousReads().toString());
        }

        if (authSpec.getBasicAuth() != null && authSpec.getBasicAuth().getEnabled()) {
            putIfNotBlank(env, EnvironmentVariables.APICURIO_AUTHN_BASIC_CLIENT_CREDENTIALS_ENABLED,
                    authSpec.getBasicAuth().getEnabled().toString());
            putIfNotBlank(env, EnvironmentVariables.APICURIO_AUTHN_BASIC_CLIENT_CREDENTIALS_CACHE_EXPIRATION,
                    authSpec.getBasicAuth().getCacheExpiration());
        }

        putIfNotBlank(env, EnvironmentVariables.OIDC_TLS_VERIFICATION,
                authSpec.getTlsVerificationType());

        Authz.configureAuthz(authSpec.getAuthz(), env);
    }
}
