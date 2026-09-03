package io.apicurio.registry.operator.resource;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;

import java.util.Map;

// TODO: Centralize labels here for use in factory, discriminators, and tests.
public final class Labels {

    private static final String APP_NAME_REGISTRY = "apicurio-registry";
    private static final String APP_NAME_OPERATOR = "apicurio-registry-operator";

    private Labels() {
    }

    public static Map<String, String> getSelectorLabels(ApicurioRegistry3 primary, String component) {
        return Map.of(
                "app", primary.getMetadata().getName(),
                "app.kubernetes.io/name", APP_NAME_REGISTRY,
                "app.kubernetes.io/component", component,
                "app.kubernetes.io/part-of", APP_NAME_REGISTRY,
                "app.kubernetes.io/instance", primary.getMetadata().getName()
        );
    }

    public static Map<String, String> getOperatorSelectorLabels() {
        return Map.of(
                "app", APP_NAME_OPERATOR,
                "app.kubernetes.io/name", APP_NAME_OPERATOR,
                "app.kubernetes.io/component", "operator",
                "app.kubernetes.io/part-of", APP_NAME_REGISTRY,
                "app.kubernetes.io/instance", APP_NAME_OPERATOR
        );
    }

    public static Map<String, String> getMinimalOperatorSelectorLabels() {
        return Map.of(
                "app", APP_NAME_OPERATOR
        );
    }

    public static Map<String, String> getOperatorManagedLabels() {
        return Map.of(
                "app.kubernetes.io/managed-by", APP_NAME_OPERATOR,
                "app.apicurio-registry-operator.io/managed", "true"
        );
    }
}
