package io.apicurio.registry.storage.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Represents editable contract metadata fields that can be set by users.
 * This DTO is used for user input when creating or updating contract metadata.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class EditableContractMetadataDto {

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
