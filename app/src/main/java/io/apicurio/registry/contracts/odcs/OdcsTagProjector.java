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

    @Inject
    @Current
    RegistryStorage storage;

    public int project(OdcsContract contract, String groupId, String artifactId,
            List<String> warnings) {
        if (contract.getSchemas() == null || contract.getSchemas().isEmpty()) {
            return 0;
        }
        try {
            String targetVersion = resolveTargetVersion(contract, groupId, artifactId);
            if (targetVersion == null) {
                warnings.add("No versions found for " + groupId + "/" + artifactId);
                return 0;
            }

            var meta = storage.getArtifactVersionMetaData(groupId, artifactId,
                    targetVersion);

            var labels = new LinkedHashMap<String, String>();
            if (meta.getLabels() != null) {
                meta.getLabels().forEach((k, v) -> {
                    if (!k.startsWith(FIELD_TAG_PREFIX)) {
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
                    count += addTags(labels, entry.getKey(), entry.getValue());
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

    private int addTags(Map<String, String> labels, String fieldPath,
            OdcsFieldMetadata field) {
        int count = 0;
        if (Boolean.TRUE.equals(field.getPii())) {
            labels.put(FIELD_TAG_PREFIX + fieldPath + "|PII", "EXTERNAL");
            count++;
        }
        if (field.getClassification() != null) {
            labels.put(FIELD_TAG_PREFIX + fieldPath + "|CLASSIFICATION:"
                    + field.getClassification().toUpperCase(Locale.ROOT), "EXTERNAL");
            count++;
        }
        if (field.getTags() != null) {
            for (String tag : field.getTags()) {
                labels.put(FIELD_TAG_PREFIX + fieldPath + "|" + tag, "EXTERNAL");
                count++;
            }
        }
        return count;
    }
}
