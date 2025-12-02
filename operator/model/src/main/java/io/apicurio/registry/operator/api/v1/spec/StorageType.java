package io.apicurio.registry.operator.api.v1.spec;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum StorageType {

    @JsonProperty("postgresql")
    POSTGRESQL("postgresql"),
    @JsonProperty("mysql")
    MYSQL("mysql"),
    @JsonProperty("kafkasql")
    KAFKASQL("kafkasql");

    private final String value;

    StorageType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Checks if this storage type is a SQL storage type (PostgreSQL or MySQL).
     *
     * @return true if this storage type is PostgreSQL or MySQL, false otherwise
     */
    public boolean isSql() {
        return this == POSTGRESQL || this == MYSQL;
    }
}
