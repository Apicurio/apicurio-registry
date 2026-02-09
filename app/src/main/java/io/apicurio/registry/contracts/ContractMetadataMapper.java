package io.apicurio.registry.contracts;

import io.apicurio.registry.storage.dto.ContractMetadataDto;
import io.apicurio.registry.storage.dto.ContractStatus;
import io.apicurio.registry.storage.dto.DataClassification;
import io.apicurio.registry.storage.dto.EditableContractMetadataDto;
import io.apicurio.registry.storage.dto.PromotionStage;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.Map;

/**
 * Mapper for converting between contract metadata DTOs and label maps.
 * Handles bidirectional conversion for storing and retrieving contract metadata from labels.
 */
@ApplicationScoped
public class ContractMetadataMapper {

    /**
     * Extracts contract metadata from a labels map.
     *
     * @param labels the labels map to extract from (may be null or empty)
     * @return the extracted contract metadata DTO
     */
    public ContractMetadataDto fromLabels(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return ContractMetadataDto.builder().build();
        }

        return ContractMetadataDto.builder()
                .status(parseEnum(labels.get(ContractLabels.STATUS), ContractStatus.class))
                .ownerTeam(labels.get(ContractLabels.OWNER_TEAM))
                .ownerDomain(labels.get(ContractLabels.OWNER_DOMAIN))
                .supportContact(labels.get(ContractLabels.SUPPORT_CONTACT))
                .classification(parseEnum(labels.get(ContractLabels.CLASSIFICATION), DataClassification.class))
                .stage(parseEnum(labels.get(ContractLabels.STAGE), PromotionStage.class))
                .stableDate(labels.get(ContractLabels.STABLE_DATE))
                .deprecatedDate(labels.get(ContractLabels.DEPRECATED_DATE))
                .deprecationReason(labels.get(ContractLabels.DEPRECATION_REASON))
                .build();
    }

    /**
     * Converts contract metadata to a labels map.
     *
     * @param metadata the contract metadata to convert
     * @return a map of labels
     */
    public Map<String, String> toLabels(ContractMetadataDto metadata) {
        Map<String, String> labels = new HashMap<>();

        if (metadata == null) {
            return labels;
        }

        addIfNotNull(labels, ContractLabels.STATUS, enumToString(metadata.getStatus()));
        addIfNotNull(labels, ContractLabels.OWNER_TEAM, metadata.getOwnerTeam());
        addIfNotNull(labels, ContractLabels.OWNER_DOMAIN, metadata.getOwnerDomain());
        addIfNotNull(labels, ContractLabels.SUPPORT_CONTACT, metadata.getSupportContact());
        addIfNotNull(labels, ContractLabels.CLASSIFICATION, enumToString(metadata.getClassification()));
        addIfNotNull(labels, ContractLabels.STAGE, enumToString(metadata.getStage()));
        addIfNotNull(labels, ContractLabels.STABLE_DATE, metadata.getStableDate());
        addIfNotNull(labels, ContractLabels.DEPRECATED_DATE, metadata.getDeprecatedDate());
        addIfNotNull(labels, ContractLabels.DEPRECATION_REASON, metadata.getDeprecationReason());

        return labels;
    }

    /**
     * Converts editable contract metadata to a labels map.
     *
     * @param metadata the editable contract metadata to convert
     * @return a map of labels
     */
    public Map<String, String> toLabels(EditableContractMetadataDto metadata) {
        Map<String, String> labels = new HashMap<>();

        if (metadata == null) {
            return labels;
        }

        addIfNotNull(labels, ContractLabels.STATUS, enumToString(metadata.getStatus()));
        addIfNotNull(labels, ContractLabels.OWNER_TEAM, metadata.getOwnerTeam());
        addIfNotNull(labels, ContractLabels.OWNER_DOMAIN, metadata.getOwnerDomain());
        addIfNotNull(labels, ContractLabels.SUPPORT_CONTACT, metadata.getSupportContact());
        addIfNotNull(labels, ContractLabels.CLASSIFICATION, enumToString(metadata.getClassification()));
        addIfNotNull(labels, ContractLabels.STAGE, enumToString(metadata.getStage()));
        addIfNotNull(labels, ContractLabels.STABLE_DATE, metadata.getStableDate());
        addIfNotNull(labels, ContractLabels.DEPRECATED_DATE, metadata.getDeprecatedDate());
        addIfNotNull(labels, ContractLabels.DEPRECATION_REASON, metadata.getDeprecationReason());

        return labels;
    }

    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumClass) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String enumToString(Enum<?> value) {
        return value != null ? value.name() : null;
    }

    private void addIfNotNull(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }
}
