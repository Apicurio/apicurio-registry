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
public class ArtifactSearchResultsDto {

    @Builder.Default
    private List<SearchedArtifactDto> artifacts = new ArrayList<>();
    private long count;
}
