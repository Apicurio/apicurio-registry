package io.apicurio.registry.operator.api.v1.spec;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum GitOpsMode {

    @JsonProperty("pull")
    PULL("pull"),
    @JsonProperty("push")
    PUSH("push");

    private final String value;

    GitOpsMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
