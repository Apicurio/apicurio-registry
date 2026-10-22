package io.apicurio.registry.operator.api.v1.spec;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum InsecureRequests {

    @JsonProperty("enabled")
    ENABLED("enabled"),
    @JsonProperty("disabled")
    DISABLED("disabled"),
    @JsonProperty("redirect")
    REDIRECT("redirect");

    private final String value;

    InsecureRequests(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
