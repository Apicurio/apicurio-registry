package io.apicurio.registry.contracts;

import io.apicurio.registry.storage.dto.ContractMetadataDto;
import io.apicurio.registry.storage.dto.ContractStatus;
import io.apicurio.registry.storage.dto.DataClassification;
import io.apicurio.registry.storage.dto.EditableContractMetadataDto;
import io.apicurio.registry.storage.dto.PromotionStage;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class ContractMetadataMapper {

    private static final Logger log = LoggerFactory.getLogger(ContractMetadataMapper.class);

    public ContractMetadataDto fromLabels(Map<String, String> labels) {
        return fromLabels(labels, null);
    }

    public ContractMetadataDto fromLabels(Map<String, String> labels, String contractId) {
        if (labels == null || labels.isEmpty()) {
            return ContractMetadataDto.builder().build();
        }

        if (contractId == null) {
            contractId = detectContractId(labels);
        }

        String p = contractId != null
                ? ContractLabels.contractPrefix(contractId) : ContractLabels.PREFIX;

        return ContractMetadataDto.builder()
                .status(parseEnum(labels.get(p + ContractLabels.SUFFIX_STATUS), ContractStatus.class))
                .ownerTeam(labels.get(p + ContractLabels.SUFFIX_OWNER_TEAM))
                .ownerDomain(labels.get(p + ContractLabels.SUFFIX_OWNER_DOMAIN))
                .supportContact(labels.get(p + ContractLabels.SUFFIX_SUPPORT_CONTACT))
                .classification(parseEnum(labels.get(p + ContractLabels.SUFFIX_CLASSIFICATION),
                        DataClassification.class))
                .stage(parseEnum(labels.get(p + ContractLabels.SUFFIX_STAGE), PromotionStage.class))
                .stableDate(labels.get(p + ContractLabels.SUFFIX_STABLE_DATE))
                .deprecatedDate(labels.get(p + ContractLabels.SUFFIX_DEPRECATED_DATE))
                .deprecationReason(labels.get(p + ContractLabels.SUFFIX_DEPRECATION_REASON))
                .compatibilityGroup(labels.get(p + ContractLabels.SUFFIX_COMPATIBILITY_GROUP))
                .build();
    }

    public Map<String, String> toLabels(ContractMetadataDto metadata) {
        return toLabels(metadata, ContractLabels.PREFIX);
    }

    public Map<String, String> toLabels(ContractMetadataDto metadata, String prefix) {
        Map<String, String> labels = new HashMap<>();
        if (metadata == null) {
            return labels;
        }
        addIfNotNull(labels, prefix + ContractLabels.SUFFIX_STATUS, enumToString(metadata.getStatus()));
        addIfNotNull(labels, prefix + ContractLabels.SUFFIX_OWNER_TEAM, metadata.getOwnerTeam());
        addIfNotNull(labels, prefix + ContractLabels.SUFFIX_OWNER_DOMAIN, metadata.getOwnerDomain());
        addIfNotNull(labels, prefix + ContractLabels.SUFFIX_SUPPORT_CONTACT, metadata.getSupportContact());
        addIfNotNull(labels, prefix + ContractLabels.SUFFIX_CLASSIFICATION,
                enumToString(metadata.getClassification()));
        addIfNotNull(labels, prefix + ContractLabels.SUFFIX_STAGE, enumToString(metadata.getStage()));
        addIfNotNull(labels, prefix + ContractLabels.SUFFIX_STABLE_DATE, metadata.getStableDate());
        addIfNotNull(labels, prefix + ContractLabels.SUFFIX_DEPRECATED_DATE, metadata.getDeprecatedDate());
        addIfNotNull(labels, prefix + ContractLabels.SUFFIX_DEPRECATION_REASON,
                metadata.getDeprecationReason());
        addIfNotNull(labels, prefix + ContractLabels.SUFFIX_COMPATIBILITY_GROUP,
                metadata.getCompatibilityGroup());
        return labels;
    }

    public Map<String, String> toLabels(EditableContractMetadataDto metadata) {
        return toLabels(metadata, ContractLabels.PREFIX);
    }

    public Map<String, String> toLabels(EditableContractMetadataDto metadata, String prefix) {
        Map<String, String> labels = new HashMap<>();
        if (metadata == null) {
            return labels;
        }
        addIfNotNull(labels, prefix + ContractLabels.SUFFIX_STATUS, enumToString(metadata.getStatus()));
        addIfNotNull(labels, prefix + ContractLabels.SUFFIX_OWNER_TEAM, metadata.getOwnerTeam());
        addIfNotNull(labels, prefix + ContractLabels.SUFFIX_OWNER_DOMAIN, metadata.getOwnerDomain());
        addIfNotNull(labels, prefix + ContractLabels.SUFFIX_SUPPORT_CONTACT, metadata.getSupportContact());
        addIfNotNull(labels, prefix + ContractLabels.SUFFIX_CLASSIFICATION,
                enumToString(metadata.getClassification()));
        addIfNotNull(labels, prefix + ContractLabels.SUFFIX_STAGE, enumToString(metadata.getStage()));
        addIfNotNull(labels, prefix + ContractLabels.SUFFIX_STABLE_DATE, metadata.getStableDate());
        addIfNotNull(labels, prefix + ContractLabels.SUFFIX_DEPRECATED_DATE, metadata.getDeprecatedDate());
        addIfNotNull(labels, prefix + ContractLabels.SUFFIX_DEPRECATION_REASON,
                metadata.getDeprecationReason());
        addIfNotNull(labels, prefix + ContractLabels.SUFFIX_COMPATIBILITY_GROUP,
                metadata.getCompatibilityGroup());
        return labels;
    }

    private String detectContractId(Map<String, String> labels) {
        String suffix = "." + ContractLabels.SUFFIX_ID;
        for (var entry : labels.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(ContractLabels.PREFIX) && key.endsWith(suffix)) {
                String middle = key.substring(ContractLabels.PREFIX.length(),
                        key.length() - suffix.length());
                if (!middle.isEmpty() && !middle.contains(".")) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumClass) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid {} value in contract label: '{}'", enumClass.getSimpleName(), value);
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
