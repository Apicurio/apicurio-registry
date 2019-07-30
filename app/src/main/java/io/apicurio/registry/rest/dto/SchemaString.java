package io.apicurio.registry.rest.dto;

import javax.json.bind.annotation.JsonbProperty;

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

    @JsonbProperty("schema")
    public String getSchemaString() {
        return schemaString;
    }

    @JsonbProperty("schema")
    public void setSchemaString(String schemaString) {
        this.schemaString = schemaString;
    }
}
