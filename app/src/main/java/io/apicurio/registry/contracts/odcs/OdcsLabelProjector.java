package io.apicurio.registry.contracts.odcs;

import io.apicurio.registry.contracts.ContractLabels;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@ApplicationScoped
public class OdcsLabelProjector {

    @Inject
    RegistryStorage storage;

    public int project(OdcsContract contract, String groupId, String artifactId) {
        var existing = storage.getArtifactMetaData(groupId, artifactId);
        var labels = new LinkedHashMap<String, String>();

        if (existing.getLabels() != null) {
            existing.getLabels().forEach((k, v) -> {
                if (!k.startsWith(ContractLabels.PREFIX)) {
                    labels.put(k, v);
                }
            });
        }

        collectLabels(contract, labels);

        storage.updateArtifactMetaData(groupId, artifactId,
                EditableArtifactMetaDataDto.builder().labels(labels).build());

        return (int) labels.keySet().stream()
                .filter(k -> k.startsWith(ContractLabels.PREFIX)).count();
    }

    private void collectLabels(OdcsContract contract, Map<String, String> labels) {
        var info = contract.getInfo();
        if (info != null) {
            put(labels, ContractLabels.STATUS, mapStatus(info.getStatus()));
            putUpper(labels, ContractLabels.CLASSIFICATION, info.getDataClassification());
            put(labels, ContractLabels.ODCS_CONTRACT_VERSION, info.getVersion());
        }

        var team = contract.getTeam();
        if (team != null) {
            put(labels, ContractLabels.OWNER_TEAM, team.getName());
            put(labels, ContractLabels.OWNER_DOMAIN, team.getDomain());
            put(labels, ContractLabels.SUPPORT_CONTACT, team.getContact());
        }

        var sl = contract.getServiceLevel();
        if (sl != null) {
            if (sl.getAvailability() != null) {
                labels.put(ContractLabels.SLA_AVAILABILITY,
                        String.valueOf(sl.getAvailability()));
            }
            var lat = sl.getLatency();
            if (lat != null) {
                put(labels, ContractLabels.SLA_LATENCY_P50, lat.getP50());
                put(labels, ContractLabels.SLA_LATENCY_P99, lat.getP99());
            }
        }

        var q = contract.getQuality();
        if (q != null && q.getFreshness() != null) {
            put(labels, ContractLabels.QUALITY_FRESHNESS_MAX_STALENESS,
                    q.getFreshness().getMaxStaleness());
        }

        put(labels, ContractLabels.ODCS_CONTRACT_ID, contract.getId());
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
