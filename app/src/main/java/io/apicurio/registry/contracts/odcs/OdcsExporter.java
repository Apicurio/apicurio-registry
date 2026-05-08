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

    public String export(String groupId, String artifactId) {
        OdcsContract contract = reconstruct(groupId, artifactId);
        return parser.serialize(contract);
    }

    public OdcsContract reconstruct(String groupId, String artifactId) {
        ArtifactMetaDataDto meta = storage.getArtifactMetaData(groupId, artifactId);
        Map<String, String> labels = meta.getLabels() != null ? meta.getLabels() : Map.of();

        OdcsInfo info = buildInfo(labels, meta);
        OdcsTeam team = buildTeam(labels);
        OdcsServiceLevel serviceLevel = buildServiceLevel(labels);
        OdcsQuality quality = buildQuality(groupId, artifactId);
        List<OdcsSchema> schemas = buildSchemas(groupId, artifactId, meta);

        return OdcsContract.builder()
                .apiVersion("v3.1.0")
                .kind("DataContract")
                .id(labels.get(ContractLabels.ODCS_CONTRACT_ID))
                .info(info)
                .team(team)
                .serviceLevel(serviceLevel)
                .quality(quality)
                .schemas(schemas.isEmpty() ? null : schemas)
                .build();
    }

    private OdcsInfo buildInfo(Map<String, String> labels, ArtifactMetaDataDto meta) {
        return OdcsInfo.builder()
                .title(meta.getName())
                .version(labels.get(ContractLabels.ODCS_CONTRACT_VERSION))
                .description(meta.getDescription())
                .status(reverseMapStatus(labels.get(ContractLabels.STATUS)))
                .dataClassification(lowerOrNull(labels.get(ContractLabels.CLASSIFICATION)))
                .build();
    }

    private OdcsTeam buildTeam(Map<String, String> labels) {
        String name = labels.get(ContractLabels.OWNER_TEAM);
        String domain = labels.get(ContractLabels.OWNER_DOMAIN);
        String contact = labels.get(ContractLabels.SUPPORT_CONTACT);
        if (name == null && domain == null && contact == null) {
            return null;
        }
        return OdcsTeam.builder().name(name).domain(domain).contact(contact).build();
    }

    private OdcsServiceLevel buildServiceLevel(Map<String, String> labels) {
        String avail = labels.get(ContractLabels.SLA_AVAILABILITY);
        String p50 = labels.get(ContractLabels.SLA_LATENCY_P50);
        String p99 = labels.get(ContractLabels.SLA_LATENCY_P99);
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

    private OdcsQuality buildQuality(String groupId, String artifactId) {
        ContractRuleSetDto ruleset = storage.getArtifactContractRuleset(groupId, artifactId);
        if (ruleset == null || ruleset.getDomainRules() == null) {
            return null;
        }

        List<OdcsAccuracyRule> accuracy = new ArrayList<>();
        for (ContractRuleDto rule : ruleset.getDomainRules()) {
            if (!rule.getName().startsWith(ODCS_RULE_PREFIX)) {
                continue;
            }
            String expr = rule.getExpr();
            if (expr != null && expr.startsWith("message.")) {
                expr = expr.substring("message.".length());
            }
            Double threshold = 1.0;
            if (rule.getParams() != null && rule.getParams().containsKey("threshold")) {
                Double parsed = parseDoubleOrNull(rule.getParams().get("threshold"));
                if (parsed != null) {
                    threshold = parsed;
                }
            }
            accuracy.add(OdcsAccuracyRule.builder()
                    .name(rule.getName().substring(ODCS_RULE_PREFIX.length()))
                    .expression(expr)
                    .threshold(threshold)
                    .build());
        }

        return accuracy.isEmpty() ? null
                : OdcsQuality.builder().accuracy(accuracy).build();
    }

    private List<OdcsSchema> buildSchemas(String groupId, String artifactId,
            ArtifactMetaDataDto meta) {
        List<String> versions = storage.getArtifactVersions(groupId, artifactId);
        if (versions == null || versions.isEmpty()) {
            return List.of();
        }

        String latest = versions.get(versions.size() - 1);
        ArtifactVersionMetaDataDto vMeta = storage.getArtifactVersionMetaData(
                groupId, artifactId, latest);
        Map<String, String> vLabels = vMeta.getLabels() != null ? vMeta.getLabels() : Map.of();

        Map<String, OdcsFieldMetadata> fields = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : vLabels.entrySet()) {
            if (!entry.getKey().startsWith(FIELD_TAG_PREFIX)) {
                continue;
            }
            String remainder = entry.getKey().substring(FIELD_TAG_PREFIX.length());
            int lastDot = remainder.lastIndexOf('.');
            if (lastDot <= 0) {
                continue;
            }
            String fieldPath = remainder.substring(0, lastDot);
            String tagName = remainder.substring(lastDot + 1);

            OdcsFieldMetadata field = fields.computeIfAbsent(fieldPath,
                    k -> OdcsFieldMetadata.builder().tags(new ArrayList<>()).build());
            if ("PII".equals(tagName)) {
                field.setPii(true);
            } else if (tagName.startsWith("CLASSIFICATION:")) {
                field.setClassification(
                        tagName.substring("CLASSIFICATION:".length())
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
