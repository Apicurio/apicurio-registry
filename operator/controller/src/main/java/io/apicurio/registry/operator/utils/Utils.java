package io.apicurio.registry.operator.utils;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;

import java.util.Map;

public class Utils {

    private Utils() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Adds an environment variable to the map only if the value is not null or blank.
     *
     * @param envVars The environment variables map.
     * @param name The name of the environment variable.
     * @param value The value to be set (ignored if null or blank).
     */
    public static void putIfNotBlank(Map<String, EnvVar> envVars, String name, String value) {
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
    public static EnvVar createEnvVar(String name, String value) {
        return new EnvVarBuilder().withName(name).withValue(value).build();
    }
}
