package io.apicurio.registry.storage.dto;

import java.util.ArrayList;
import java.util.List;

public class VersionSearchResultsDto {

    private long count;
    private List<SearchedVersionDto> versions = new ArrayList<SearchedVersionDto>();

    /**
     * Constructor.
     */
    public VersionSearchResultsDto() {
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

    /**
     * @return the versions
     */
    public List<SearchedVersionDto> getVersions() {
        return versions;
    }

    /**
     * @param versions the versions to set
     */
    public void setVersions(List<SearchedVersionDto> versions) {
        this.versions = versions;
    }

}
