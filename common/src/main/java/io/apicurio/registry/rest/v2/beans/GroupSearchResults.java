
package io.apicurio.registry.rest.v2.beans;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Describes the response received when searching for groups.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "groups",
    "count"
})
@Generated("jsonschema2pojo")
@io.quarkus.runtime.annotations.RegisterForReflection
@lombok.ToString
public class GroupSearchResults {

    /**
     * The groups returned in the result set.
     * (Required)
     * 
     */
    @JsonProperty("groups")
    @JsonPropertyDescription("The groups returned in the result set.")
    private List<SearchedGroup> groups = new ArrayList<SearchedGroup>();
    /**
     * The total number of groups that matched the query that produced the result set (may be 
     * more than the number of groups in the result set).
     * (Required)
     * 
     */
    @JsonProperty("count")
    @JsonPropertyDescription("The total number of groups that matched the query that produced the result set (may be \nmore than the number of groups in the result set).")
    private Integer count;

    /**
     * The groups returned in the result set.
     * (Required)
     * 
     */
    @JsonProperty("groups")
    public List<SearchedGroup> getGroups() {
        return groups;
    }

    /**
     * The groups returned in the result set.
     * (Required)
     * 
     */
    @JsonProperty("groups")
    public void setGroups(List<SearchedGroup> groups) {
        this.groups = groups;
    }

    /**
     * The total number of groups that matched the query that produced the result set (may be 
     * more than the number of groups in the result set).
     * (Required)
     * 
     */
    @JsonProperty("count")
    public Integer getCount() {
        return count;
    }

    /**
     * The total number of groups that matched the query that produced the result set (may be 
     * more than the number of groups in the result set).
     * (Required)
     * 
     */
    @JsonProperty("count")
    public void setCount(Integer count) {
        this.count = count;
    }

}
