package io.apicurio.registry.operator.utils;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import java.util.Map;
import java.util.function.Function;

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
     * @param name    The name of the environment variable.
     * @param value   The value to be set (ignored if null or blank).
     */
    public static void putIfNotBlank(Map<String, EnvVar> envVars, String name, String value) {
        if (!isBlank(value)) {
            envVars.put(name, createEnvVar(name, value));
        }
    }

    /**
     * Creates an environment variable using the given name and value.
     *
     * @param name  The name of the environment variable.
     * @param value The value of the environment variable.
     * @return An {@link EnvVar} instance with the specified name and value.
     */
    public static EnvVar createEnvVar(String name, String value) {
        return new EnvVarBuilder().withName(name).withValue(value).build();
    }

    /**
     * @param action    (Optionally) modify the resource, return true if the resource has been modified and should be updated.
     * @param onSuccess run a function after the resource has been successfully updated.
     */
    public static <T extends HasMetadata> void updateResourceManually(Context<ApicurioRegistry3> context, T resource, Function<T, Boolean> action, Runnable onSuccess) {
        var fresh = context.getClient().resource(resource).get();
        if (fresh != null) {
            if (action.apply(fresh)) {
                context.getClient().resource(fresh).update();
                onSuccess.run();
            }
        }
    }

    /**
     * @param action (Optionally) modify the resource, return true if the resource has been modified and should be updated.
     */
    public static <T extends HasMetadata> void updateResourceManually(Context<ApicurioRegistry3> context, T resource, Function<T, Boolean> action) {
        // @formatter:off
        updateResourceManually(context, resource, action, () -> {});
        // @formatter:on
    }
}
