package io.apicurio.registry.operator.feat.security;

import io.apicurio.registry.operator.EnvironmentVariables;
import io.apicurio.registry.operator.api.v1.spec.auth.AdminOverrideSpec;
import io.fabric8.kubernetes.api.model.EnvVar;

import java.util.Map;

import static io.apicurio.registry.operator.utils.Utils.createEnvVar;
import static io.apicurio.registry.operator.utils.Utils.putIfNotBlank;

/**
 * Helper class used to handle Admin Overide related configuration.
 */
public class AdminOverride {

    /**
     * Configures admin-override-related environment variables for the Apicurio Registry.
     *
     * @param env The map of environment variables to be configured.
     * @param adminOverrideSpec The adminOverride specification containing required admin override settings.
     *            If null, no changes will be made to envVars.
     */
    public static void configureAdminOverride(AdminOverrideSpec adminOverrideSpec, Map<String, EnvVar> env) {
        if (adminOverrideSpec == null) {
            return;
        }

        if (adminOverrideSpec.getEnabled() != null && adminOverrideSpec.getEnabled()) {
            env.put(EnvironmentVariables.APICURIO_AUTH_ADMIN_OVERRIDE_ENABLED,
                    createEnvVar(EnvironmentVariables.APICURIO_AUTH_ADMIN_OVERRIDE_ENABLED,
                            adminOverrideSpec.getEnabled().toString()));

            putIfNotBlank(env, EnvironmentVariables.APICURIO_AUTH_ADMIN_OVERRIDE_ROLE,
                    adminOverrideSpec.getRole());

            putIfNotBlank(env, EnvironmentVariables.APICURIO_AUTH_ADMIN_OVERRIDE_FROM,
                    adminOverrideSpec.getFrom());
            putIfNotBlank(env, EnvironmentVariables.APICURIO_AUTH_ADMIN_OVERRIDE_TYPE,
                    adminOverrideSpec.getType());
            putIfNotBlank(env, EnvironmentVariables.APICURIO_AUTH_ADMIN_OVERRIDE_CLAIM,
                    adminOverrideSpec.getClaimName());
            putIfNotBlank(env, EnvironmentVariables.APICURIO_AUTH_ADMIN_OVERRIDE_CLAIM_VALUE,
                    adminOverrideSpec.getClaimValue());
        }
    }
}
