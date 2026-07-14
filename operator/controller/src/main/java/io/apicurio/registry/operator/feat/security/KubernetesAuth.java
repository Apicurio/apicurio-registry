package io.apicurio.registry.operator.feat.security;

import io.apicurio.registry.operator.EnvironmentVariables;
import io.apicurio.registry.operator.api.v1.spec.auth.KubernetesAuthSpec;
import io.apicurio.registry.operator.api.v1.spec.auth.KubernetesGroupMappingSpec;
import io.fabric8.kubernetes.api.model.EnvVar;

import java.util.Map;

import static io.apicurio.registry.operator.utils.Utils.createEnvVar;
import static io.apicurio.registry.operator.utils.Utils.putIfNotBlank;

public class KubernetesAuth {

    public static void configureKubernetesAuth(KubernetesAuthSpec spec, Map<String, EnvVar> env) {
        if (spec == null || !Boolean.TRUE.equals(spec.getEnabled())) {
            return;
        }

        env.put(EnvironmentVariables.APICURIO_AUTHN_KUBERNETES_ENABLED,
                createEnvVar(EnvironmentVariables.APICURIO_AUTHN_KUBERNETES_ENABLED, "true"));

        if (!env.containsKey(EnvironmentVariables.APICURIO_AUTHN_MECHANISM_PRIORITY)) {
            env.put(EnvironmentVariables.APICURIO_AUTHN_MECHANISM_PRIORITY,
                    createEnvVar(EnvironmentVariables.APICURIO_AUTHN_MECHANISM_PRIORITY,
                            "kubernetes,basic,proxy-header,oidc"));
        }

        putIfNotBlank(env, EnvironmentVariables.APICURIO_AUTHN_KUBERNETES_API_AUDIENCES,
                spec.getApiAudiences());

        if (spec.getCacheExpiration() != null) {
            env.put(EnvironmentVariables.APICURIO_AUTHN_KUBERNETES_CACHE_EXPIRATION,
                    createEnvVar(EnvironmentVariables.APICURIO_AUTHN_KUBERNETES_CACHE_EXPIRATION,
                            spec.getCacheExpiration().toString()));
        }

        KubernetesGroupMappingSpec groupMapping = spec.getGroupMapping();
        if (groupMapping != null) {
            env.put(EnvironmentVariables.APICURIO_AUTH_ROLE_SOURCE,
                    createEnvVar(EnvironmentVariables.APICURIO_AUTH_ROLE_SOURCE, "kubernetes"));

            putIfNotBlank(env,
                    EnvironmentVariables.APICURIO_AUTH_ROLE_SOURCE_KUBERNETES_GROUP_MAPPING_ADMIN,
                    groupMapping.getAdminGroups());
            putIfNotBlank(env,
                    EnvironmentVariables.APICURIO_AUTH_ROLE_SOURCE_KUBERNETES_GROUP_MAPPING_DEVELOPER,
                    groupMapping.getDeveloperGroups());
            putIfNotBlank(env,
                    EnvironmentVariables.APICURIO_AUTH_ROLE_SOURCE_KUBERNETES_GROUP_MAPPING_READONLY,
                    groupMapping.getReadOnlyGroups());
        }
    }
}
