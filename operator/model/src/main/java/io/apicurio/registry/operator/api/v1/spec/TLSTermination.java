package io.apicurio.registry.operator.api.v1.spec;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TLSTermination {

    @JsonProperty("edge")
    EDGE("edge"),
    @JsonProperty("passthrough")
    PASSTHROUGH("passthrough"),
    @JsonProperty("reencrypt")
    REENCRYPT("reencrypt");

    private final String value;

    TLSTermination(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
