package io.apicurio.registry.operator.feat;

import io.apicurio.registry.operator.EnvironmentVariables;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.ComponentSpec;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.apicurio.registry.operator.utils.IngressUtils;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Helper class used to handle CORS related configuration.
 */
public class Cors {
    /**
     * Configure the QUARKUS_HTTP_CORS_ORIGINS environment variable with the following:
     * <ul>
     * <li>Add the ingress host</li>
     * <li>Override if QUARKUS_HTTP_CORS_ORIGINS is configured in the "env" section</li>
     * </ul>
     * 
     * @param primary
     * @param envVars
     */
    public static void configureAllowedOrigins(ApicurioRegistry3 primary,
            LinkedHashMap<String, EnvVar> envVars) {
        TreeSet<String> allowedOrigins = new TreeSet<>();

        // If the QUARKUS_HTTP_CORS_ORIGINS env var is configured in the "env" section of the CR,
        // then make sure to add those configured values to the set of allowed origins we want to
        // configure.
        Optional.ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp).map(ComponentSpec::getEnv)
                .ifPresent(env -> {
                    env.stream().filter(
                            envVar -> envVar.getName().equals(EnvironmentVariables.QUARKUS_HTTP_CORS_ORIGINS))
                            .forEach(envVar -> {
                                Optional.ofNullable(envVar.getValue()).ifPresent(envVarValue -> {
                                    Arrays.stream(envVarValue.split(",")).forEach(allowedOrigins::add);
                                });
                            });
                });

        // If not, let's try to figure it out from other sources.
        if (allowedOrigins.isEmpty()) {
            // If there is a configured Ingress host for the UI, add it to the allowed
            // origins.
            String host = IngressUtils.getConfiguredHost(ResourceFactory.COMPONENT_UI, primary);
            if (host != null) {
                allowedOrigins.add("http://" + host);
                allowedOrigins.add("https://" + host);
            }
        }

        // If we still do not have anything, then default to "*"
        if (allowedOrigins.isEmpty()) {
            allowedOrigins.add("*");
        }

        // Join the values in allowedOrigins into a String and set it as the new value of the env var.
        String envVarValue = String.join(",", allowedOrigins);
        envVars.put(EnvironmentVariables.QUARKUS_HTTP_CORS_ORIGINS, new EnvVarBuilder()
                .withName(EnvironmentVariables.QUARKUS_HTTP_CORS_ORIGINS).withValue(envVarValue).build());
    }
}
