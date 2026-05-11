package io.apicurio.registry.contracts.odcs;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.contracts.ContractLabels;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContractRuleDto;
import io.apicurio.registry.storage.dto.ContractRuleSetDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ApplicationScoped
public class OdcsExporter {

    private static final String FIELD_TAG_PREFIX = OdcsTagProjector.FIELD_TAG_PREFIX;
    private static final String ODCS_RULE_PREFIX = OdcsRuleProjector.ODCS_RULE_PREFIX;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    OdcsParser parser;

    public String export(String groupId, String artifactId, String contractId) {
        OdcsContract contract = reconstruct(groupId, artifactId, contractId);
        return parser.serialize(contract);
    }

    public OdcsContract reconstruct(String groupId, String artifactId,
            String contractId) {
        ArtifactMetaDataDto meta = storage.getArtifactMetaData(groupId, artifactId);
        Map<String, String> labels = meta.getLabels() != null ? meta.getLabels() : Map.of();

        String prefix = ContractLabels.PREFIX + contractId + ".";

        OdcsInfo info = buildInfo(labels, prefix, meta);
        OdcsTeam team = buildTeam(labels, prefix);
        OdcsServiceLevel serviceLevel = buildServiceLevel(labels, prefix);
        OdcsQuality quality = buildQuality(groupId, artifactId, labels, prefix,
                contractId);
        List<OdcsSchema> schemas = buildSchemas(groupId, artifactId, meta, contractId);

        return OdcsContract.builder()
                .apiVersion("v3.1.0")
                .kind("DataContract")
                .id(contractId)
                .info(info)
                .team(team)
                .serviceLevel(serviceLevel)
                .quality(quality)
                .schemas(schemas.isEmpty() ? null : schemas)
                .build();
    }

    private OdcsInfo buildInfo(Map<String, String> labels, String prefix,
            ArtifactMetaDataDto meta) {
        return OdcsInfo.builder()
                .title(meta.getName())
                .version(labels.get(prefix + "version"))
                .description(meta.getDescription())
                .status(reverseMapStatus(labels.get(prefix + "status")))
                .dataClassification(lowerOrNull(labels.get(prefix + "classification")))
                .build();
    }

    private OdcsTeam buildTeam(Map<String, String> labels, String prefix) {
        String name = labels.get(prefix + "owner.team");
        String domain = labels.get(prefix + "owner.domain");
        String contact = labels.get(prefix + "support.contact");
        if (name == null && domain == null && contact == null) {
            return null;
        }
        return OdcsTeam.builder().name(name).domain(domain).contact(contact).build();
    }

    private OdcsServiceLevel buildServiceLevel(Map<String, String> labels,
            String prefix) {
        String avail = labels.get(prefix + "sla.availability");
        String p50 = labels.get(prefix + "sla.latency.p50");
        String p99 = labels.get(prefix + "sla.latency.p99");
        if (avail == null && p50 == null && p99 == null) {
            return null;
        }
        OdcsLatency latency = (p50 != null || p99 != null)
                ? OdcsLatency.builder().p50(p50).p99(p99).build()
                : null;
        return OdcsServiceLevel.builder()
                .availability(parseDoubleOrNull(avail))
                .latency(latency)
                .build();
    }

    private OdcsQuality buildQuality(String groupId, String artifactId,
            Map<String, String> labels, String prefix, String contractId) {
        String rulePrefix = ODCS_RULE_PREFIX + contractId + ":";

        ContractRuleSetDto ruleset = storage.getArtifactContractRuleset(groupId,
                artifactId);

        List<OdcsAccuracyRule> accuracy = new ArrayList<>();
        if (ruleset != null && ruleset.getDomainRules() != null) {
            for (ContractRuleDto rule : ruleset.getDomainRules()) {
                if (!rule.getName().startsWith(rulePrefix)) {
                    continue;
                }
                String expr = rule.getExpr();
                Double threshold = 1.0;
                if (rule.getParams() != null
                        && rule.getParams().containsKey("threshold")) {
                    Double parsed = parseDoubleOrNull(
                            rule.getParams().get("threshold"));
                    if (parsed != null) {
                        threshold = parsed;
                    }
                }
                accuracy.add(OdcsAccuracyRule.builder()
                        .name(rule.getName().substring(rulePrefix.length()))
                        .expression(expr)
                        .threshold(threshold)
                        .build());
            }
        }

        String maxStaleness = labels.get(prefix + "quality.freshness.maxStaleness");
        OdcsFreshness freshness = maxStaleness != null
                ? OdcsFreshness.builder().maxStaleness(maxStaleness).build()
                : null;

        String completenessPrefix = prefix + "quality.completeness.";
        List<OdcsCompletenessRule> completeness = new ArrayList<>();
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            if (entry.getKey().startsWith(completenessPrefix)) {
                String field = entry.getKey().substring(completenessPrefix.length());
                Double threshold = parseDoubleOrNull(entry.getValue());
                if (threshold != null) {
                    completeness.add(OdcsCompletenessRule.builder()
                            .field(field).threshold(threshold).build());
                }
            }
        }

        if (accuracy.isEmpty() && freshness == null && completeness.isEmpty()) {
            return null;
        }
        return OdcsQuality.builder()
                .accuracy(accuracy.isEmpty() ? null : accuracy)
                .freshness(freshness)
                .completeness(completeness.isEmpty() ? null : completeness)
                .build();
    }

    private List<OdcsSchema> buildSchemas(String groupId, String artifactId,
            ArtifactMetaDataDto meta, String contractId) {
        List<String> versions = storage.getArtifactVersions(groupId, artifactId);
        if (versions == null || versions.isEmpty()) {
            return List.of();
        }

        String latest = versions.get(versions.size() - 1);
        ArtifactVersionMetaDataDto vMeta = storage.getArtifactVersionMetaData(
                groupId, artifactId, latest);
        Map<String, String> vLabels = vMeta.getLabels() != null
                ? vMeta.getLabels()
                : Map.of();

        String tagPrefix = FIELD_TAG_PREFIX + contractId + ":";
        Map<String, OdcsFieldMetadata> fields = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : vLabels.entrySet()) {
            if (!entry.getKey().startsWith(tagPrefix)) {
                continue;
            }
            String remainder = entry.getKey().substring(tagPrefix.length());
            int separator = remainder.indexOf('|');
            if (separator <= 0) {
                continue;
            }
            String fieldPath = remainder.substring(0, separator);
            String tagName = remainder.substring(separator + 1);

            OdcsFieldMetadata field = fields.computeIfAbsent(fieldPath,
                    k -> OdcsFieldMetadata.builder().tags(new ArrayList<>()).build());
            if ("PII".equals(tagName)) {
                field.setPii(true);
            } else if (tagName.startsWith("CLASSIFICATION:")) {
                field.setClassification(tagName.substring("CLASSIFICATION:".length())
                        .toLowerCase(Locale.ROOT));
            } else {
                field.getTags().add(tagName);
            }
        }

        if (fields.isEmpty()) {
            return List.of();
        }

        String location = groupId != null
                ? groupId + "/" + artifactId + ":" + latest
                : artifactId + ":" + latest;
        return List.of(OdcsSchema.builder()
                .name(meta.getName())
                .type(lowerOrNull(meta.getArtifactType()))
                .location(location)
                .fields(fields)
                .build());
    }

    private static String reverseMapStatus(String contractStatus) {
        if (contractStatus == null) {
            return null;
        }
        return switch (contractStatus) {
        case "DRAFT" -> "draft";
        case "STABLE" -> "active";
        case "DEPRECATED" -> "deprecated";
        default -> "draft";
        };
    }

    private static String lowerOrNull(String value) {
        return value != null ? value.toLowerCase(Locale.ROOT) : null;
    }

    private static Double parseDoubleOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
