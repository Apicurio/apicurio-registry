package io.apicurio.registry.storage.dto;

import java.util.ArrayList;
import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class RoleMappingSearchResultsDto {

    @Builder.Default
    private List<RoleMappingDto> roleMappings = new ArrayList<>();
    private long count;

}
