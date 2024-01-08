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

    private List<SearchedGroupDto> groups = new ArrayList<SearchedGroupDto>();

    private Integer count;
}
