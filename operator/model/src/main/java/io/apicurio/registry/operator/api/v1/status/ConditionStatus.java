package io.apicurio.registry.operator.api.v1.status;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ConditionStatus {

    // spotless:off
    @JsonProperty("True")
    TRUE("True"),
    @JsonProperty("False")
    FALSE("False"),
    @JsonProperty("Unknown")
    UNKNOWN("Unknown");
    // spotless:on

    String value;

    ConditionStatus(String value) {
        this.value = value;
    }

    @JsonValue()
    public String getValue() {
        return value;
    }
}
