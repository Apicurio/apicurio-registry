package io.apicurio.registry.ibmcompat.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class SchemaSummary {

    private String id;
    private String name;
    private SchemaState state;
    private boolean enabled;

    /**
     * Lower-case URL-encoded version of the schema name.
     **/

    @JsonProperty("id")
    @NotNull
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * User-provided name for a schema. Not necessarily unique.
     **/

    @JsonProperty("name")
    @NotNull
    @Size(min = 1, max = 100)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     *
     **/

    @JsonProperty("state")
    @NotNull
    public SchemaState getState() {
        return state;
    }

    public void setState(SchemaState state) {
        this.state = state;
    }

    /**
     * Set to false if the schema is disabled. If the schema is disabled, all the schema versions are disabled.
     **/

    @JsonProperty("enabled")
    @NotNull
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }


    @Override
    public boolean equals(java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SchemaSummary schemaSummary = (SchemaSummary) o;
        return Objects.equals(id, schemaSummary.id) &&
               Objects.equals(name, schemaSummary.name) &&
               Objects.equals(state, schemaSummary.state) &&
               Objects.equals(enabled, schemaSummary.enabled);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, state, enabled);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class SchemaSummary {\n");

        sb.append("    id: ").append(toIndentedString(id)).append("\n");
        sb.append("    name: ").append(toIndentedString(name)).append("\n");
        sb.append("    state: ").append(toIndentedString(state)).append("\n");
        sb.append("    enabled: ").append(toIndentedString(enabled)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Convert the given object to string with each line indented by 4 spaces
     * (except the first line).
     */
    protected String toIndentedString(java.lang.Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }
}

