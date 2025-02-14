package io.apicurio.registry.operator.resource;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;

import java.util.Map;

// TODO: Centralize labels here for use in factory, discriminators, and tests.
public final class Labels {

    private Labels() {
    }

    public static Map<String, String> getSelectorLabels(ApicurioRegistry3 primary, String component) {
        return Map.of(
                "app", primary.getMetadata().getName(),
                "app.kubernetes.io/name", "apicurio-registry",
                "app.kubernetes.io/component", component,
                "app.kubernetes.io/instance", primary.getMetadata().getName(),
                "app.kubernetes.io/part-of", "apicurio-registry"
        );
    }
}
