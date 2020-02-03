package io.apicurio.registry.ibmcompat.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class SchemaInfo {

    private String id;
    private String name;
    private SchemaState state;
    private Boolean enabled;
    private List<SchemaVersion> versions = new ArrayList<SchemaVersion>();

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
    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     *
     **/

    @JsonProperty("versions")
    @NotNull
    public List<SchemaVersion> getVersions() {
        return versions;
    }

    public void setVersions(List<SchemaVersion> versions) {
        this.versions = versions;
    }


    @Override
    public boolean equals(java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SchemaInfo schemaInfo = (SchemaInfo) o;
        return Objects.equals(id, schemaInfo.id) &&
               Objects.equals(name, schemaInfo.name) &&
               Objects.equals(state, schemaInfo.state) &&
               Objects.equals(enabled, schemaInfo.enabled) &&
               Objects.equals(versions, schemaInfo.versions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, state, enabled, versions);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class SchemaInfo {\n");

        sb.append("    id: ").append(toIndentedString(id)).append("\n");
        sb.append("    name: ").append(toIndentedString(name)).append("\n");
        sb.append("    state: ").append(toIndentedString(state)).append("\n");
        sb.append("    enabled: ").append(toIndentedString(enabled)).append("\n");
        sb.append("    versions: ").append(toIndentedString(versions)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Convert the given object to string with each line indented by 4 spaces
     * (except the first line).
     */
    private String toIndentedString(java.lang.Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }
}

