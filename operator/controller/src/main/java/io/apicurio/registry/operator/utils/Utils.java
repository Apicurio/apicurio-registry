package io.apicurio.registry.operator.utils;

import io.apicurio.registry.operator.OperatorException;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceKey;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static io.apicurio.registry.operator.resource.Labels.getSelectorLabels;
import static io.apicurio.registry.operator.utils.Mapper.copy;
import static io.javaoperatorsdk.operator.processing.event.ResourceID.fromResource;
import static java.lang.Long.parseLong;
import static java.util.Comparator.comparingLong;

public class Utils {

    private static final Logger log = LoggerFactory.getLogger(Utils.class);

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

    public static <T> T copyForSSA(T original) {
        var copied = copy(original);
        if (copied instanceof HasMetadata) {
            ((HasMetadata) copied).getMetadata().setManagedFields(null);
        }
        return copied;
    }

    public static <R extends HasMetadata> Optional<R> getSecondaryResource(Context<ApicurioRegistry3> context, ApicurioRegistry3 primary, ResourceKey<R> key) {
        var labels = getSelectorLabels(primary, key.getComponent());

        var resources = context.getSecondaryResources(key.getKlass())
                .stream()
                .filter(r -> r.getMetadata().getLabels().entrySet().containsAll(labels.entrySet()))
                .toList();

        return switch (resources.size()) {
            case 0 -> Optional.empty();
            case 1 -> Optional.of(resources.get(0));
            default -> {
                var msg = "Expected at most one %s resource with labels %s but got more: %s"
                        .formatted(key.getKlass().getSimpleName(), labels, resources.stream().map(ResourceID::fromResource).toList());
                if (resources.stream().allMatch(r -> fromResource(resources.get(0)).equals(fromResource(r)))) {
                    log.debug(msg);
                    yield resources.stream().max(comparingLong(r -> parseLong(r.getMetadata().getResourceVersion())));
                } else {
                    throw new OperatorException(msg);
                }
            }
        };
    }
}
