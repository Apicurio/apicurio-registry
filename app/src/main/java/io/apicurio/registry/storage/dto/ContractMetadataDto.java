package io.apicurio.registry.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Represents the full contract metadata extracted from labels.
 * This DTO contains all contract-related information stored in the reserved contract.* label namespace.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class ContractMetadataDto {

    private ContractStatus status;
    private String ownerTeam;
    private String ownerDomain;
    private String supportContact;
    private DataClassification classification;
    private PromotionStage stage;
    private String stableDate;
    private String deprecatedDate;
    private String deprecationReason;
}
