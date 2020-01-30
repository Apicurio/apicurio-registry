package io.apicurio.registry.ibmcompat.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import javax.validation.constraints.NotNull;

@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class SchemaListItem extends SchemaSummary {

    private SchemaVersion latest;

    /**
     *
     **/

    @JsonProperty("latest")
    @NotNull
    public SchemaVersion getLatest() {
        return latest;
    }

    public void setLatest(SchemaVersion latest) {
        this.latest = latest;
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
        SchemaListItem schemaListItem = (SchemaListItem) o;
        return Objects.equals(latest, schemaListItem.latest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), latest);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class SchemaListItem {\n");

        sb.append(super.toString());
        sb.append("    latest: ").append(toIndentedString(latest)).append("\n");
        sb.append("}");
        return sb.toString();
    }
}

