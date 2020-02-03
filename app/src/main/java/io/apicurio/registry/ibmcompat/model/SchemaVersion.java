package io.apicurio.registry.ibmcompat.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;
import java.util.Objects;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class SchemaVersion {

    private Integer id;
    private String name;
    private Date date;
    private SchemaState state;
    private Boolean enabled;

    /**
     * Server-managed unique ID for the version.  Guaranteed to be unique within this schema only (different schemas will use the same version IDs).
     * minimum: 1
     **/

    @JsonProperty("id")
    @NotNull
    @Min(1)
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * Client-provided description of a version.  Enforced to be unique within this schema only (different schemas may use the same version names).
     **/

    @JsonProperty("name")
    @NotNull
    @Size(max = 50)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Timestamp for when a schema version.  When used for an overall schema, this will be the timestamp for when the most recent version was created.
     **/

    @JsonProperty("date")
    @NotNull
    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
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
     * Set to false if the version of the schema is disabled.
     **/

    @JsonProperty("enabled")
    @NotNull
    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
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
        SchemaVersion schemaVersion = (SchemaVersion) o;
        return Objects.equals(id, schemaVersion.id) &&
               Objects.equals(name, schemaVersion.name) &&
               Objects.equals(date, schemaVersion.date) &&
               Objects.equals(state, schemaVersion.state) &&
               Objects.equals(enabled, schemaVersion.enabled);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, date, state, enabled);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class SchemaVersion {\n");

        sb.append("    id: ").append(toIndentedString(id)).append("\n");
        sb.append("    name: ").append(toIndentedString(name)).append("\n");
        sb.append("    date: ").append(toIndentedString(date)).append("\n");
        sb.append("    state: ").append(toIndentedString(state)).append("\n");
        sb.append("    enabled: ").append(toIndentedString(enabled)).append("\n");
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

