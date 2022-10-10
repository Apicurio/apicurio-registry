
package io.apicurio.registry.rest.v2.beans;

import java.util.Date;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Models a single group from the result set returned when searching for groups.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "id",
    "description",
    "createdOn",
    "createdBy",
    "modifiedOn",
    "modifiedBy"
})
@Generated("jsonschema2pojo")
@io.quarkus.runtime.annotations.RegisterForReflection
@lombok.ToString
public class SearchedGroup {

    /**
     * An ID of a single artifact group.
     * (Required)
     * 
     */
    @JsonProperty("id")
    @JsonPropertyDescription("An ID of a single artifact group.")
    private String id;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("description")
    @JsonPropertyDescription("")
    private String description;
    /**
     * 
     * (Required)
     * 
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssZ", timezone = "UTC")
    @JsonProperty("createdOn")
    @JsonPropertyDescription("")
    private Date createdOn;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("createdBy")
    @JsonPropertyDescription("")
    private Object createdBy;
    /**
     * 
     * (Required)
     * 
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssZ", timezone = "UTC")
    @JsonProperty("modifiedOn")
    @JsonPropertyDescription("")
    private Date modifiedOn;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("modifiedBy")
    @JsonPropertyDescription("")
    private String modifiedBy;

    /**
     * An ID of a single artifact group.
     * (Required)
     * 
     */
    @JsonProperty("id")
    public String getId() {
        return id;
    }

    /**
     * An ID of a single artifact group.
     * (Required)
     * 
     */
    @JsonProperty("id")
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("createdOn")
    public Date getCreatedOn() {
        return createdOn;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("createdOn")
    public void setCreatedOn(Date createdOn) {
        this.createdOn = createdOn;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("createdBy")
    public Object getCreatedBy() {
        return createdBy;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("createdBy")
    public void setCreatedBy(Object createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("modifiedOn")
    public Date getModifiedOn() {
        return modifiedOn;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("modifiedOn")
    public void setModifiedOn(Date modifiedOn) {
        this.modifiedOn = modifiedOn;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("modifiedBy")
    public String getModifiedBy() {
        return modifiedBy;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("modifiedBy")
    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

}
