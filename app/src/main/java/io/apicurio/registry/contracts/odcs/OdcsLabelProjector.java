package io.apicurio.registry.contracts.odcs;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.contracts.ContractLabels;
import io.apicurio.registry.contracts.ContractMetadataValidator;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ContractStatus;
import io.apicurio.registry.storage.error.InvalidContractMetadataException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ApplicationScoped
public class OdcsLabelProjector {

    /**
     * Contract state that is changed through the REST API rather than by submitting YAML. A
     * re-projection keeps it, so updating a contract does not undo a promotion, a compatibility
     * group or a lifecycle date.
     */
    private static final List<String> API_MANAGED_SUFFIXES = List.of(
            ContractLabels.SUFFIX_STAGE,
            ContractLabels.SUFFIX_COMPATIBILITY_GROUP,
            ContractLabels.SUFFIX_STABLE_DATE,
            ContractLabels.SUFFIX_DEPRECATED_DATE,
            ContractLabels.SUFFIX_DEPRECATION_REASON);

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    ContractMetadataValidator validator;

    /**
     * Writes the contract's labels onto the artifact, replacing the labels of an earlier
     * projection of the same contract.
     *
     * @return the number of labels taken from the contract
     */
    public int project(OdcsContract contract, String contractId, String groupId,
            String artifactId, List<String> warnings) {
        String prefix = ContractLabels.contractPrefix(contractId);
        var labels = new LinkedHashMap<String, String>();
        collectLabels(contract, contractId, prefix, labels);
        int fromContract = labels.size();

        Map<String, String> existing = storage.getArtifactMetaData(groupId, artifactId).getLabels();
        if (existing == null) {
            existing = Map.of();
        }
        for (String suffix : API_MANAGED_SUFFIXES) {
            String key = prefix + suffix;
            if (existing.containsKey(key)) {
                labels.put(key, existing.get(key));
            }
        }
        applyStatus(prefix, existing, labels, groupId + "/" + artifactId, warnings);

        storage.mergeArtifactLabels(groupId, artifactId, prefix, labels);
        return fromContract;
    }

    /**
     * Removes every label the contract put on the artifact, including state set through the API.
     */
    public void remove(String contractId, String groupId, String artifactId) {
        storage.mergeArtifactLabels(groupId, artifactId, ContractLabels.contractPrefix(contractId),
                Map.of());
    }

    /**
     * Applies the status from the contract only if the lifecycle allows moving to it from the
     * current status, as POST .../contract/status does; otherwise keeps the current status. A
     * status that changes to STABLE or DEPRECATED records its lifecycle date.
     */
    private void applyStatus(String prefix, Map<String, String> existing, Map<String, String> labels,
            String target, List<String> warnings) {
        String statusKey = prefix + ContractLabels.SUFFIX_STATUS;
        ContractStatus current = parseStatus(existing.get(statusKey));
        ContractStatus requested = parseStatus(labels.get(statusKey));
        if (requested == null || requested == current) {
            if (current != null) {
                labels.put(statusKey, current.name());
            }
            return;
        }
        try {
            validator.validateStatusTransition(current, requested);
        } catch (InvalidContractMetadataException e) {
            labels.put(statusKey, current.name());
            warnings.add("Status " + requested + " from the contract not applied to " + target
                    + ": the contract is already " + current + ", and its status cannot move back.");
            return;
        }
        String today = LocalDate.now(ZoneId.systemDefault()).toString();
        if (requested == ContractStatus.STABLE) {
            labels.putIfAbsent(prefix + ContractLabels.SUFFIX_STABLE_DATE, today);
        } else if (requested == ContractStatus.DEPRECATED) {
            labels.putIfAbsent(prefix + ContractLabels.SUFFIX_DEPRECATED_DATE, today);
        }
    }

    private static ContractStatus parseStatus(String value) {
        if (value == null) {
            return null;
        }
        try {
            return ContractStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void collectLabels(OdcsContract contract, String contractId, String prefix,
            Map<String, String> labels) {
        var info = contract.getInfo();
        if (info != null) {
            put(labels, prefix + ContractLabels.SUFFIX_STATUS, mapStatus(info.getStatus()));
            putUpper(labels, prefix + ContractLabels.SUFFIX_CLASSIFICATION,
                    info.getDataClassification());
            put(labels, prefix + ContractLabels.SUFFIX_VERSION, info.getVersion());
        }

        var team = contract.getTeam();
        if (team != null) {
            put(labels, prefix + ContractLabels.SUFFIX_OWNER_TEAM, team.getName());
            put(labels, prefix + ContractLabels.SUFFIX_OWNER_DOMAIN, team.getDomain());
            put(labels, prefix + ContractLabels.SUFFIX_SUPPORT_CONTACT, team.getContact());
        }

        var sl = contract.getServiceLevel();
        if (sl != null) {
            collectServiceLevelLabels(sl, prefix, labels);
        }

        var q = contract.getQuality();
        if (q != null) {
            collectQualityLabels(q, prefix, labels);
        }

        labels.put(prefix + ContractLabels.SUFFIX_ID, contractId);
    }

    private void collectServiceLevelLabels(OdcsServiceLevel sl, String prefix, Map<String, String> labels) {
        if (sl.getAvailability() != null) {
            labels.put(prefix + ContractLabels.SUFFIX_SLA_AVAILABILITY,
                    String.valueOf(sl.getAvailability()));
        }
        var lat = sl.getLatency();
        if (lat != null) {
            put(labels, prefix + ContractLabels.SUFFIX_SLA_LATENCY_P50, lat.getP50());
            put(labels, prefix + ContractLabels.SUFFIX_SLA_LATENCY_P99, lat.getP99());
        }
    }

    private void collectQualityLabels(OdcsQuality q, String prefix, Map<String, String> labels) {
        if (q.getFreshness() != null) {
            put(labels, prefix + ContractLabels.SUFFIX_QUALITY_FRESHNESS_MAX_STALENESS,
                    q.getFreshness().getMaxStaleness());
        }
        if (q.getCompleteness() != null) {
            for (var rule : q.getCompleteness()) {
                if (rule.getField() != null && rule.getThreshold() != null) {
                    labels.put(prefix + "quality.completeness." + rule.getField(),
                            String.valueOf(rule.getThreshold()));
                }
            }
        }
    }

    static String mapStatus(String odcsStatus) {
        if (odcsStatus == null) {
            return null;
        }
        return switch (odcsStatus.toLowerCase(Locale.ROOT)) {
        case "draft" -> "DRAFT";
        case "active" -> "STABLE";
        case "deprecated", "retired" -> "DEPRECATED";
        default -> "DRAFT";
        };
    }

    private static void put(Map<String, String> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static void putUpper(Map<String, String> map, String key, String value) {
        if (value != null) {
            map.put(key, value.toUpperCase(Locale.ROOT));
        }
    }
}
