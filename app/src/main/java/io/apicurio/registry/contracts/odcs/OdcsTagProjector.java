package io.apicurio.registry.contracts.odcs;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.model.VersionId;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorage.RetrievalBehavior;
import io.apicurio.registry.storage.error.NotFoundException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@ApplicationScoped
public class OdcsTagProjector {

    static final String FIELD_TAG_PREFIX = "field-tag.";
    /** The longest label key every storage variant accepts. */
    static final int MAX_LABEL_KEY_LENGTH = 256;

    @Inject
    @Current
    RegistryStorage storage;

    /**
     * Replaces the contract's field tags on the version the schema's location names. Missing
     * versions and branches are reported as warnings; any other failure propagates, so that the
     * caller can roll the contract back.
     *
     * @return the number of tags projected
     */
    public int project(OdcsContract contract, String contractId, String groupId,
            String artifactId, List<String> warnings) {
        if (contract.getSchemas() == null || contract.getSchemas().isEmpty()) {
            return 0;
        }
        OdcsSchema matchingSchema = findMatchingSchema(contract.getSchemas(), groupId, artifactId);
        if (matchingSchema == null) {
            return 0;
        }

        String targetVersion;
        try {
            targetVersion = resolveTargetVersion(matchingSchema, groupId, artifactId);
        } catch (NotFoundException e) {
            warnings.add("Version not found for " + matchingSchema.getLocation() + ". Field tags skipped.");
            return 0;
        }

        String tagPrefix = tagPrefix(contractId);
        var labels = new LinkedHashMap<String, String>();
        int count = 0;
        if (matchingSchema.getFields() != null) {
            for (var entry : matchingSchema.getFields().entrySet()) {
                count += addTags(labels, tagPrefix, entry.getKey(), entry.getValue(), warnings);
            }
        }
        // Also written when there are no tags, to clear the tags of an earlier projection
        replaceTags(contractId, groupId, artifactId, targetVersion, labels);
        return count;
    }

    /**
     * Removes the contract's field tags from every version of the artifact.
     */
    public void remove(String contractId, String groupId, String artifactId) {
        for (String version : storage.getArtifactVersions(groupId, artifactId, Set.of())) {
            replaceTags(contractId, groupId, artifactId, version, Map.of());
        }
    }

    /**
     * The contract's field tags on every version of the artifact, keyed by version, including tags
     * written in lower case before label keys kept their case. Versions without tags are omitted.
     */
    public Map<String, Map<String, String>> tagsByVersion(String contractId, String groupId,
            String artifactId) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (String version : storage.getArtifactVersions(groupId, artifactId, Set.of())) {
            Map<String, String> tags = new LinkedHashMap<>();
            versionLabels(groupId, artifactId, version).forEach((key, value) -> {
                if (isTagOf(contractId, key)) {
                    tags.put(key, value);
                }
            });
            if (!tags.isEmpty()) {
                result.put(version, tags);
            }
        }
        return result;
    }

    /**
     * Replaces the contract's field tags on one version with the given ones. Tags written before
     * label keys kept their case are under a lower-cased prefix, and are removed too.
     */
    public void replaceTags(String contractId, String groupId, String artifactId, String version,
            Map<String, String> tags) {
        String prefix = tagPrefix(contractId);
        String legacyPrefix = prefix.toLowerCase(Locale.ROOT);
        Map<String, String> current = versionLabels(groupId, artifactId, version);
        if (!legacyPrefix.equals(prefix)
                && current.keySet().stream().anyMatch(key -> key.startsWith(legacyPrefix))) {
            storage.mergeVersionLabels(groupId, artifactId, version, legacyPrefix, Map.of());
        }
        if (!tags.isEmpty() || current.keySet().stream().anyMatch(key -> key.startsWith(prefix))) {
            storage.mergeVersionLabels(groupId, artifactId, version, prefix, tags);
        }
    }

    private static String tagPrefix(String contractId) {
        return FIELD_TAG_PREFIX + contractId + ":";
    }

    private static boolean isTagOf(String contractId, String key) {
        String prefix = tagPrefix(contractId);
        return key.startsWith(prefix) || key.startsWith(prefix.toLowerCase(Locale.ROOT));
    }

    private Map<String, String> versionLabels(String groupId, String artifactId, String version) {
        Map<String, String> labels = storage.getArtifactVersionMetaData(groupId, artifactId, version)
                .getLabels();
        return labels != null ? labels : Map.of();
    }

    /**
     * Returns the {@code schemas[]} entry whose {@code location} resolves to the target artifact.
     * Group may be omitted in {@code location} (defaults to {@code groupId}).
     * <p>
     * If multiple entries resolve to the same {@code (groupId, artifactId)}, the first match in
     * list order is used intentionally — duplicate locations should be avoided in contract YAML.
     */
    static OdcsSchema findMatchingSchema(List<OdcsSchema> schemas, String groupId, String artifactId) {
        if (schemas == null) {
            return null;
        }
        for (OdcsSchema schema : schemas) {
            if (matchesTarget(schema, groupId, artifactId)) {
                return schema;
            }
        }
        return null;
    }

    static boolean matchesTarget(OdcsSchema schema, String groupId, String artifactId) {
        if (schema == null) {
            return false;
        }
        String[] parsed = OdcsSchemaLocations.parse(schema.getLocation(), groupId);
        return OdcsSchemaLocations.isValid(parsed)
                && Objects.equals(groupId, parsed[0])
                && Objects.equals(artifactId, parsed[1]);
    }

    private String resolveTargetVersion(OdcsSchema schema, String groupId, String artifactId) {
        String versionExpression = versionExpressionOf(schema.getLocation());

        if (versionExpression != null && !versionExpression.isBlank()) {
            if (versionExpression.contains("=")) {
                String branchName = versionExpression.split("=", 2)[1];
                GAV gav = storage.getBranchTip(new GA(groupId, artifactId),
                        new BranchId(branchName), RetrievalBehavior.SKIP_DISABLED_LATEST);
                return gav.getRawVersionId();
            }
            if (VersionId.isValid(versionExpression)) {
                try {
                    storage.getArtifactVersionMetaData(groupId, artifactId,
                            versionExpression);
                    return versionExpression;
                } catch (VersionNotFoundException e) {
                    // Not a version: try it as a branch name
                }
            }
            GAV gav = storage.getBranchTip(new GA(groupId, artifactId),
                    new BranchId(versionExpression), RetrievalBehavior.SKIP_DISABLED_LATEST);
            return gav.getRawVersionId();
        }

        // No version given: the latest version (the order of getArtifactVersions is not defined)
        return storage.getBranchTip(new GA(groupId, artifactId), BranchId.LATEST,
                RetrievalBehavior.SKIP_DISABLED_LATEST).getRawVersionId();
    }

    private static String versionExpressionOf(String location) {
        String versionExpression = null;
        if (location != null && location.contains(":")) {
            String afterColon = location.substring(location.indexOf(':') + 1);
            if (!afterColon.isEmpty() && !afterColon.isBlank()) {
                versionExpression = afterColon;
            }
        }
        return versionExpression;
    }

    private int addTags(Map<String, String> labels, String tagPrefix, String fieldPath,
            OdcsFieldMetadata field, List<String> warnings) {
        int count = 0;
        if (Boolean.TRUE.equals(field.getPii())) {
            count += putTag(labels, tagPrefix, fieldPath, "PII", warnings);
        }
        if (field.getClassification() != null) {
            count += putTag(labels, tagPrefix, fieldPath,
                    "CLASSIFICATION:" + field.getClassification().toUpperCase(Locale.ROOT),
                    warnings);
        }
        if (field.getTags() != null) {
            for (String tag : field.getTags()) {
                count += putTag(labels, tagPrefix, fieldPath, tag, warnings);
            }
        }
        return count;
    }

    private int putTag(Map<String, String> labels, String tagPrefix, String fieldPath,
            String tagName, List<String> warnings) {
        String key = tagPrefix + fieldPath + "|" + tagName;
        if (key.length() > MAX_LABEL_KEY_LENGTH) {
            warnings.add("Tag label key too long (max " + MAX_LABEL_KEY_LENGTH
                    + "): " + fieldPath + "|" + tagName);
            return 0;
        }
        labels.put(key, "EXTERNAL");
        return 1;
    }
}
