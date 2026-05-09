package io.apicurio.registry.contracts.odcs;

import io.apicurio.registry.cdi.Current;
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
    @Current
    RegistryStorage storage;

    public int project(OdcsContract contract, String contractId, String groupId,
            String artifactId) {
        String prefix = ContractLabels.PREFIX + contractId + ".";

        var existing = storage.getArtifactMetaData(groupId, artifactId);
        var labels = new LinkedHashMap<String, String>();

        if (existing.getLabels() != null) {
            existing.getLabels().forEach((k, v) -> {
                if (!k.startsWith(prefix)) {
                    labels.put(k, v);
                }
            });
        }

        collectLabels(contract, contractId, prefix, labels);

        storage.updateArtifactMetaData(groupId, artifactId,
                EditableArtifactMetaDataDto.builder().labels(labels).build());

        return (int) labels.keySet().stream().filter(k -> k.startsWith(prefix)).count();
    }

    private void collectLabels(OdcsContract contract, String contractId, String prefix,
            Map<String, String> labels) {
        var info = contract.getInfo();
        if (info != null) {
            put(labels, prefix + "status", mapStatus(info.getStatus()));
            putUpper(labels, prefix + "classification", info.getDataClassification());
            put(labels, prefix + "version", info.getVersion());
        }

        var team = contract.getTeam();
        if (team != null) {
            put(labels, prefix + "owner.team", team.getName());
            put(labels, prefix + "owner.domain", team.getDomain());
            put(labels, prefix + "support.contact", team.getContact());
        }

        var sl = contract.getServiceLevel();
        if (sl != null) {
            if (sl.getAvailability() != null) {
                labels.put(prefix + "sla.availability",
                        String.valueOf(sl.getAvailability()));
            }
            var lat = sl.getLatency();
            if (lat != null) {
                put(labels, prefix + "sla.latency.p50", lat.getP50());
                put(labels, prefix + "sla.latency.p99", lat.getP99());
            }
        }

        var q = contract.getQuality();
        if (q != null) {
            if (q.getFreshness() != null) {
                put(labels, prefix + "quality.freshness.maxStaleness",
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

        labels.put(prefix + "id", contractId);
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
