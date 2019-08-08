package io.apicurio.registry.ccompat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Ales Justin
 */
public class SchemaString {

    private String schemaString;

    public SchemaString() {
    }

    public SchemaString(String schemaString) {
        this.schemaString = schemaString;
    }

    @JsonProperty("schema")
    public String getSchemaString() {
        return schemaString;
    }

    @JsonProperty("schema")
    public void setSchemaString(String schemaString) {
        this.schemaString = schemaString;
    }
}
