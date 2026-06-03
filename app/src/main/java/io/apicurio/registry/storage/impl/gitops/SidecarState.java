package io.apicurio.registry.storage.impl.gitops;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * States written by the sidecar in the {@link ValidationRequest} status field.
 */
public enum SidecarState {

    FETCHING("fetching"),
    FETCHED("fetched"),
    FAILED("failed");

    private final String value;

    SidecarState(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public static SidecarState fromValue(String value) {
        for (SidecarState s : values()) {
            if (s.value.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
