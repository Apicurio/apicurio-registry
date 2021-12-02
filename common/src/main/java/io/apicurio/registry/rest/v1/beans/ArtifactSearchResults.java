
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
    "artifacts",
    "count"
})
public class ArtifactSearchResults {

    /**
     * The artifacts that matched the search criteria.
     * (Required)
     * 
     */
    @JsonProperty("artifacts")
    @JsonPropertyDescription("The artifacts that matched the search criteria.")
    private List<SearchedArtifact> artifacts = new ArrayList<SearchedArtifact>();
    /**
     * The total number of artifacts that matched the search criteria.
     * (Required)
     * 
     */
    @JsonProperty("count")
    @JsonPropertyDescription("The total number of artifacts that matched the search criteria.")
    private Integer count;

    /**
     * The artifacts that matched the search criteria.
     * (Required)
     * 
     */
    @JsonProperty("artifacts")
    public List<SearchedArtifact> getArtifacts() {
        return artifacts;
    }

    /**
     * The artifacts that matched the search criteria.
     * (Required)
     * 
     */
    @JsonProperty("artifacts")
    public void setArtifacts(List<SearchedArtifact> artifacts) {
        this.artifacts = artifacts;
    }

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

    @Override
    public String toString() {
        return "ArtifactSearchResults{" +
                "artifacts=" + artifacts +
                ", count=" + count +
                '}';
    }
}
