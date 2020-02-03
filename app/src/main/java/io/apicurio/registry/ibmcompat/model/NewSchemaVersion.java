package io.apicurio.registry.ibmcompat.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class NewSchemaVersion {

    private String version;
    private String definition;
    private Integer versionid;
    private SchemaState versionstate;
    private Boolean versionenabled;

    /**
     * The name to give this version of the schema
     **/

    @JsonProperty("version")
    @NotNull
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
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

    /**
     * Requested unique ID for the version.  Must be unique within this schema only (different schemas will use the same version IDs).
     * minimum: 1
     **/

    @JsonProperty("versionid")
    @Min(1)
    public Integer getVersionid() {
        return versionid;
    }

    public void setVersionid(Integer versionid) {
        this.versionid = versionid;
    }

    /**
     *
     **/

    @JsonProperty("versionstate")
    public SchemaState getVersionstate() {
        return versionstate;
    }

    public void setVersionstate(SchemaState versionstate) {
        this.versionstate = versionstate;
    }

    /**
     * Set to false if the version of the schema is disabled.
     **/

    @JsonProperty("versionenabled")
    public Boolean getVersionenabled() {
        return versionenabled;
    }

    public void setVersionenabled(Boolean versionenabled) {
        this.versionenabled = versionenabled;
    }


    @Override
    public boolean equals(java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NewSchemaVersion newSchemaVersion = (NewSchemaVersion) o;
        return Objects.equals(version, newSchemaVersion.version) &&
               Objects.equals(definition, newSchemaVersion.definition) &&
               Objects.equals(versionid, newSchemaVersion.versionid) &&
               Objects.equals(versionstate, newSchemaVersion.versionstate) &&
               Objects.equals(versionenabled, newSchemaVersion.versionenabled);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, definition, versionid, versionstate, versionenabled);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class NewSchemaVersion {\n");

        sb.append("    version: ").append(toIndentedString(version)).append("\n");
        sb.append("    definition: ").append(toIndentedString(definition)).append("\n");
        sb.append("    versionid: ").append(toIndentedString(versionid)).append("\n");
        sb.append("    versionstate: ").append(toIndentedString(versionstate)).append("\n");
        sb.append("    versionenabled: ").append(toIndentedString(versionenabled)).append("\n");
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

