package io.apicurio.registry.contracts.odcs;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
            String targetVersion = resolveTargetVersion(contract, groupId, artifactId);
            if (targetVersion == null) {
                warnings.add("No versions found for " + groupId + "/" + artifactId);
                return 0;
            }

            String tagPrefix = FIELD_TAG_PREFIX + contractId + ":";

            var meta = storage.getArtifactVersionMetaData(groupId, artifactId,
                    targetVersion);

            var labels = new LinkedHashMap<String, String>();
            if (meta.getLabels() != null) {
                meta.getLabels().forEach((k, v) -> {
                    if (!k.startsWith(tagPrefix)) {
                        labels.put(k, v);
                    }
                });
            }

            int count = 0;
            for (OdcsSchema schema : contract.getSchemas()) {
                if (schema.getFields() == null) {
                    continue;
                }
                for (var entry : schema.getFields().entrySet()) {
                    count += addTags(labels, tagPrefix, entry.getKey(),
                            entry.getValue(), warnings);
                }
            }

            if (count > 0) {
                storage.updateArtifactVersionMetaData(groupId, artifactId,
                        targetVersion,
                        EditableVersionMetaDataDto.builder().labels(labels).build());
            }
            return count;
        } catch (Exception e) {
            warnings.add("Failed to project field tags: " + e.getMessage());
            log.warn("Failed to project field tags for {}/{}", groupId, artifactId, e);
            return 0;
        }
    }

    private String resolveTargetVersion(OdcsContract contract, String groupId,
            String artifactId) {
        if (contract.getSchemas() != null && !contract.getSchemas().isEmpty()) {
            String location = contract.getSchemas().get(0).getLocation();
            if (location != null && location.contains(":")) {
                String version = location.substring(location.indexOf(':') + 1);
                if (!version.isBlank()) {
                    return version;
                }
            }
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
