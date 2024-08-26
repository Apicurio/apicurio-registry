package io.apicurio.registry.operator.api.v1.status;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ConditionStatus {

    @JsonProperty("True")
    TRUE("True"), @JsonProperty("False")
    FALSE("False"), @JsonProperty("Unknown")
    UNKNOWN("Unknown");

    String value;

    ConditionStatus(String value) {
        this.value = value;
    }

    @JsonValue()
    public String getValue() {
        return value;
    }
}
