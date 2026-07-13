package io.apicurio.registry.contracts.odcs;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.model.VersionId;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorage.RetrievalBehavior;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@ApplicationScoped
public class OdcsTagProjector {

    private static final Logger log = LoggerFactory.getLogger(OdcsTagProjector.class);
    static final String FIELD_TAG_PREFIX = "field-tag.";
    private static final int MAX_LABEL_KEY_LENGTH = 512;

    @Inject
    @Current
    RegistryStorage storage;

    public int project(OdcsContract contract, String contractId, String groupId,
            String artifactId, List<String> warnings) {
        if (contract.getSchemas() == null || contract.getSchemas().isEmpty()) {
            return 0;
        }
        try {
            OdcsSchema matchingSchema = findMatchingSchema(contract.getSchemas(), groupId, artifactId);
            if (matchingSchema == null) {
                return 0;
            }

            String targetVersion = resolveTargetVersion(matchingSchema, groupId, artifactId);
            if (targetVersion == null) {
                warnings.add("No versions found for " + groupId + "/" + artifactId);
                return 0;
            }

            String tagPrefix = FIELD_TAG_PREFIX + contractId + ":";

            var labels = new LinkedHashMap<String, String>();
            int count = 0;
            if (matchingSchema.getFields() != null) {
                for (var entry : matchingSchema.getFields().entrySet()) {
                    count += addTags(labels, tagPrefix, entry.getKey(),
                            entry.getValue(), warnings);
                }
            }

            if (count > 0) {
                storage.mergeVersionLabels(groupId, artifactId, targetVersion,
                        tagPrefix, labels);
            }
            return count;
        } catch (Exception e) {
            warnings.add("Failed to project field tags: " + e.getMessage());
            log.warn("Failed to project field tags for {}/{}", groupId, artifactId, e);
            return 0;
        }
    }

    /**
     * Returns the {@code schemas[]} entry whose {@code location} resolves to the target artifact.
     * Group may be omitted in {@code location} (defaults to {@code groupId}).
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
        return parsed != null
                && Objects.equals(groupId, parsed[0])
                && Objects.equals(artifactId, parsed[1]);
    }

    private String resolveTargetVersion(OdcsSchema schema, String groupId, String artifactId) {
        String versionExpression = null;
        String location = schema.getLocation();
        if (location != null && location.contains(":")) {
            versionExpression = location.substring(location.indexOf(':') + 1);
        }

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
                } catch (Exception e) {
                    // Not a real version — try as branch name
                }
            }
            GAV gav = storage.getBranchTip(new GA(groupId, artifactId),
                    new BranchId(versionExpression), RetrievalBehavior.SKIP_DISABLED_LATEST);
            return gav.getRawVersionId();
        }

        var versions = storage.getArtifactVersions(groupId, artifactId);
        return (versions != null && !versions.isEmpty())
                ? versions.get(versions.size() - 1)
                : null;
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
