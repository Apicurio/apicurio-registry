package io.apicurio.registry.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Represents the quality scoring result for a data contract.
 * This DTO is used for future quality assessment functionality.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class QualityScoreDto {

    private Integer overallScore;
    private Integer documentationScore;
    private Integer schemaQualityScore;
    private Integer compatibilityScore;
    private String lastEvaluatedDate;
}
