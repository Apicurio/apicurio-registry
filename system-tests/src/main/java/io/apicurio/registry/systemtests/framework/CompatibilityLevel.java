package io.apicurio.registry.systemtests.framework;

public enum CompatibilityLevel {
    BACKWARD("BACKWARD"),
    BACKWARD_TRANSITIVE("BACKWARD_TRANSITIVE"),
    FORWARD("FORWARD"),
    FORWARD_TRANSITIVE("FORWARD_TRANSITIVE"),
    FULL("FULL"),
    FULL_TRANSITIVE("FULL_TRANSITIVE"),
    NONE("NONE");

    CompatibilityLevel(String value) {
    }
}
