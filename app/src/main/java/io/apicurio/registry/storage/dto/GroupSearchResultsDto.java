package io.apicurio.registry.storage.dto;

import java.util.ArrayList;
import java.util.List;

public class GroupSearchResultsDto {

    private List<SearchedGroupDto> groups = new ArrayList<SearchedGroupDto>();

    private Integer count;

    public List<SearchedGroupDto> getGroups() {
        return groups;
    }

    public void setGroups(List<SearchedGroupDto> groups) {
        this.groups = groups;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }
}
