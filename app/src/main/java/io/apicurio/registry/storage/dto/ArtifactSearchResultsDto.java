package io.apicurio.registry.storage.dto;

import java.util.ArrayList;
import java.util.List;


public class ArtifactSearchResultsDto {
    
    private List<SearchedArtifactDto> artifacts = new ArrayList<SearchedArtifactDto>();
    private long count;
    
    /**
     * Constructor.
     */
    public ArtifactSearchResultsDto() {
    }

    /**
     * @return the artifacts
     */
    public List<SearchedArtifactDto> getArtifacts() {
        return artifacts;
    }

    /**
     * @param artifacts the artifacts to set
     */
    public void setArtifacts(List<SearchedArtifactDto> artifacts) {
        this.artifacts = artifacts;
    }

    /**
     * @return the count
     */
    public long getCount() {
        return count;
    }

    /**
     * @param count the count to set
     */
    public void setCount(long count) {
        this.count = count;
    }

}
