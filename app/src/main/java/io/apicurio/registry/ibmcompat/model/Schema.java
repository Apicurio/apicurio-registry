package io.apicurio.registry.ibmcompat.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import javax.validation.constraints.NotNull;

@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class Schema extends SchemaSummary {

    private SchemaVersion version;
    private String definition;

    /**
     *
     **/

    @JsonProperty("version")
    @NotNull
    public SchemaVersion getVersion() {
        return version;
    }

    public void setVersion(SchemaVersion version) {
        this.version = version;
    }

    /**
     * Avro schema definition
     **/

    @JsonProperty("definition")
    @NotNull
    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }


    @Override
    public boolean equals(java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) return false;
        Schema schema = (Schema) o;
        return Objects.equals(version, schema.version) &&
               Objects.equals(definition, schema.definition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), version, definition);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class Schema {\n");

        sb.append(super.toString());
        sb.append("    version: ").append(toIndentedString(version)).append("\n");
        sb.append("    definition: ").append(toIndentedString(definition)).append("\n");
        sb.append("}");
        return sb.toString();
    }
}

