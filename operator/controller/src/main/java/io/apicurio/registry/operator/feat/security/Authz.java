package io.apicurio.registry.operator.feat.security;

import io.apicurio.registry.operator.EnvironmentVariables;
import io.apicurio.registry.operator.api.v1.spec.auth.AuthzSpec;
import io.fabric8.kubernetes.api.model.EnvVar;

import java.util.Map;

import static io.apicurio.registry.operator.utils.Utils.createEnvVar;
import static io.apicurio.registry.operator.utils.Utils.putIfNotBlank;

/**
 * Helper class used to handle AUTHZ related configuration.
 */
public class Authz {

    /**
     * Configures authorization-related environment variables for the Apicurio Registry.
     *
     * @param env The map of environment variables to be configured.
     * @param authzSpec The auhtorization specification containing required authz settings. If null, no
     *            changes will be made to envVars.
     */
    public static void configureAuthz(AuthzSpec authzSpec, Map<String, EnvVar> env) {
        if (authzSpec == null) {
            return;
        }

        if (Boolean.parseBoolean(authzSpec.getEnabled())) {
            env.put(EnvironmentVariables.APICURIO_AUTH_ROLE_BASED_AUTHORIZATION, createEnvVar(
                    EnvironmentVariables.APICURIO_AUTH_ROLE_BASED_AUTHORIZATION, authzSpec.getEnabled()));

            putIfNotBlank(env, EnvironmentVariables.APICURIO_AUTH_OWNER_ONLY_AUTHORIZATION,
                    authzSpec.getOwnerOnly());
            putIfNotBlank(env, EnvironmentVariables.APICURIO_AUTH_OWNER_ONLY_AUTHORIZATION_LIMIT_GROUP_ACCESS,
                    authzSpec.getGroupAccess());
            putIfNotBlank(env, EnvironmentVariables.APICURIO_AUTH_AUTHENTICATED_READ_ACCESS_ENABLED,
                    authzSpec.getReadAccess());
            putIfNotBlank(env, EnvironmentVariables.APICURIO_AUTH_ROLE_SOURCE, authzSpec.getRoleSource());
            putIfNotBlank(env, EnvironmentVariables.APICURIO_AUTH_ROLES_ADMIN, authzSpec.getDeveloperRole());
            putIfNotBlank(env, EnvironmentVariables.APICURIO_AUTH_ROLES_DEVELOPER,
                    authzSpec.getDeveloperRole());
            putIfNotBlank(env, EnvironmentVariables.APICURIO_AUTH_ROLES_READONLY,
                    authzSpec.getReadOnlyRole());

            AdminOverride.configureAdminOverride(authzSpec.getAdminOverride(), env);
        }
    }
}
