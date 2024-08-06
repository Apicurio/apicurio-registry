package io.apicurio.registry.storage.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class GroupSearchResultsDto {

    @Builder.Default
    private List<SearchedGroupDto> groups = new ArrayList<SearchedGroupDto>();

    private Integer count;
}
