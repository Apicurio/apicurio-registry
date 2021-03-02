
package io.apicurio.registry.cncf.schemaregistry.beans;

import java.util.Date;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "id",
    "description",
    "createdtimeutc",
    "updatedtimeutc",
    "format",
    "groupProperties"
})
public class SchemaGroup {

    @JsonProperty("id")
    private String id;
    @JsonProperty("description")
    private String description;
    @JsonProperty("createdtimeutc")
    private Date createdtimeutc;
    @JsonProperty("updatedtimeutc")
    private Date updatedtimeutc;
    @JsonProperty("format")
    private String format;
    /**
     * Set of properties for a schemagroup.
     * 
     */
    @JsonProperty("groupProperties")
    @JsonPropertyDescription("Set of properties for a schemagroup.")
    private Map<String, String> groupProperties;

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    @JsonProperty("id")
    public void setId(String id) {
        this.id = id;
    }

    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
    }

    @JsonProperty("createdtimeutc")
    public Date getCreatedtimeutc() {
        return createdtimeutc;
    }

    @JsonProperty("createdtimeutc")
    public void setCreatedtimeutc(Date createdtimeutc) {
        this.createdtimeutc = createdtimeutc;
    }

    @JsonProperty("updatedtimeutc")
    public Date getUpdatedtimeutc() {
        return updatedtimeutc;
    }

    @JsonProperty("updatedtimeutc")
    public void setUpdatedtimeutc(Date updatedtimeutc) {
        this.updatedtimeutc = updatedtimeutc;
    }

    @JsonProperty("format")
    public String getFormat() {
        return format;
    }

    @JsonProperty("format")
    public void setFormat(String format) {
        this.format = format;
    }

    /**
     * Set of properties for a schemagroup.
     * 
     */
    @JsonProperty("groupProperties")
    public Map<String, String> getGroupProperties() {
        return groupProperties;
    }

    /**
     * Set of properties for a schemagroup.
     * 
     */
    @JsonProperty("groupProperties")
    public void setGroupProperties(Map<String, String> groupProperties) {
        this.groupProperties = groupProperties;
    }

}
