package io.apicurio.registry.contracts.odcs;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.contracts.ContractLabels;
import io.apicurio.registry.storage.RegistryStorage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@ApplicationScoped
public class OdcsLabelProjector {

    @Inject
    @Current
    RegistryStorage storage;

    public int project(OdcsContract contract, String contractId, String groupId,
            String artifactId) {
        String prefix = ContractLabels.contractPrefix(contractId);
        var labels = new LinkedHashMap<String, String>();
        collectLabels(contract, contractId, prefix, labels);

        storage.mergeArtifactLabels(groupId, artifactId, prefix, labels);
        return labels.size();
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

        var q = contract.getQuality();
        if (q != null) {
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

        labels.put(prefix + ContractLabels.SUFFIX_ID, contractId);
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
