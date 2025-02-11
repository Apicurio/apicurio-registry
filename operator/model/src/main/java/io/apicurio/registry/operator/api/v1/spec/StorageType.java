package io.apicurio.registry.operator.api.v1.spec;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum StorageType {

    @JsonProperty("postgresql")
    POSTGRESQL("postgresql"),
    @JsonProperty("kafkasql")
    KAFKASQL("kafkasql");

    final String value;

    StorageType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
