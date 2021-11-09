
package io.apicurio.registry.rest.v1.beans;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Describes the response received when searching for artifacts.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "count",
    "versions"
})
public class VersionSearchResults {

    /**
     * The total number of artifacts that matched the search criteria.
     * (Required)
     * 
     */
    @JsonProperty("count")
    @JsonPropertyDescription("The total number of artifacts that matched the search criteria.")
    private Integer count;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("versions")
    @JsonPropertyDescription("")
    private List<SearchedVersion> versions = new ArrayList<SearchedVersion>();

    /**
     * The total number of artifacts that matched the search criteria.
     * (Required)
     * 
     */
    @JsonProperty("count")
    public Integer getCount() {
        return count;
    }

    /**
     * The total number of artifacts that matched the search criteria.
     * (Required)
     * 
     */
    @JsonProperty("count")
    public void setCount(Integer count) {
        this.count = count;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("versions")
    public List<SearchedVersion> getVersions() {
        return versions;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("versions")
    public void setVersions(List<SearchedVersion> versions) {
        this.versions = versions;
    }

    @Override
    public String toString() {
        return "VersionSearchResults{" +
                "count=" + count +
                ", versions=" + versions +
                '}';
    }
}
