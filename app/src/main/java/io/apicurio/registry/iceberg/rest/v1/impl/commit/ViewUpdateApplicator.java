package io.apicurio.registry.iceberg.rest.v1.impl.commit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies Iceberg view updates to a mutable metadata map. All updates within a single commit are applied
 * sequentially to produce one new metadata state.
 */
public final class ViewUpdateApplicator {

    private ViewUpdateApplicator() {
    }

    /**
     * Applies all updates to the metadata map (mutates it in place).
     * @param updates list of update objects, each with an "action" field
     * @param metadata mutable metadata map to apply updates to
     * @throws IllegalArgumentException if an update action is unknown
     */
    @SuppressWarnings("unchecked")
    public static void apply(List<Map<String, Object>> updates, Map<String, Object> metadata) {
        if (updates == null || updates.isEmpty()) {
            return;
        }

        for (Map<String, Object> update : updates) {
            String action = (String) update.get("action");
            if (action == null) {
                throw new IllegalArgumentException("Update is missing 'action' field");
            }

            switch (action) {
                case "assign-uuid":
                    metadata.put("view-uuid", update.get("uuid"));
                    break;
                case "upgrade-format-version":
                    applyUpgradeFormatVersion(update, metadata);
                    break;
                case "add-schema":
                    applyAddSchema(update, metadata);
                    break;
                case "set-location":
                    metadata.put("location", update.get("location"));
                    break;
                case "set-properties":
                    applySetProperties(update, metadata);
                    break;
                case "remove-properties":
                    applyRemoveProperties(update, metadata);
                    break;
                case "add-view-version":
                    applyAddViewVersion(update, metadata);
                    break;
                case "set-current-view-version":
                    applySetCurrentViewVersion(update, metadata);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown update action: " + action);
            }
        }
    }

    private static void applyUpgradeFormatVersion(Map<String, Object> update,
            Map<String, Object> metadata) {
        int newVersion = toInt(update.get("format-version"));
        int currentVersion = toInt(metadata.getOrDefault("format-version", 1));
        if (newVersion >= currentVersion) {
            metadata.put("format-version", newVersion);
        }
    }

    @SuppressWarnings("unchecked")
    private static void applyAddSchema(Map<String, Object> update, Map<String, Object> metadata) {
        Map<String, Object> schema = (Map<String, Object>) update.get("schema");
        if (schema == null) {
            throw new IllegalArgumentException("add-schema update is missing 'schema' field");
        }

        List<Object> schemas = getMutableList(metadata, "schemas");

        if (!schema.containsKey("schema-id")) {
            schema = new HashMap<>(schema);
            schema.put("schema-id", schemas.size());
        }

        schemas.add(schema);
        metadata.put("schemas", schemas);
    }

    @SuppressWarnings("unchecked")
    private static void applySetProperties(Map<String, Object> update, Map<String, Object> metadata) {
        Map<String, Object> updates = (Map<String, Object>) update.get("updates");
        if (updates == null || updates.isEmpty()) {
            return;
        }

        Map<String, Object> properties = (Map<String, Object>) metadata.get("properties");
        if (properties == null) {
            properties = new HashMap<>();
        } else {
            properties = new HashMap<>(properties);
        }
        properties.putAll(updates);
        metadata.put("properties", properties);
    }

    @SuppressWarnings("unchecked")
    private static void applyRemoveProperties(Map<String, Object> update, Map<String, Object> metadata) {
        List<String> removals = (List<String>) update.get("removals");
        if (removals == null || removals.isEmpty()) {
            return;
        }

        Map<String, Object> properties = (Map<String, Object>) metadata.get("properties");
        if (properties != null) {
            properties = new HashMap<>(properties);
            for (String key : removals) {
                properties.remove(key);
            }
            metadata.put("properties", properties);
        }
    }

    @SuppressWarnings("unchecked")
    private static void applyAddViewVersion(Map<String, Object> update, Map<String, Object> metadata) {
        Map<String, Object> viewVersion = (Map<String, Object>) update.get("view-version");
        if (viewVersion == null) {
            throw new IllegalArgumentException("add-view-version update is missing 'view-version' field");
        }

        List<Object> versions = getMutableList(metadata, "versions");

        // Auto-assign version-id if not present
        int versionId;
        if (viewVersion.containsKey("version-id")) {
            versionId = toInt(viewVersion.get("version-id"));
        } else {
            versionId = versions.size() + 1;
            viewVersion = new HashMap<>(viewVersion);
            viewVersion.put("version-id", versionId);
        }

        // Set timestamp if not present
        if (!viewVersion.containsKey("timestamp-ms")) {
            viewVersion = viewVersion instanceof HashMap ? viewVersion : new HashMap<>(viewVersion);
            viewVersion.put("timestamp-ms", System.currentTimeMillis());
        }

        versions.add(viewVersion);
        metadata.put("versions", versions);
        metadata.put("current-version-id", versionId);

        // Update version-log
        List<Object> versionLog = getMutableList(metadata, "version-log");
        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("version-id", versionId);
        logEntry.put("timestamp-ms", viewVersion.get("timestamp-ms"));
        versionLog.add(logEntry);
        metadata.put("version-log", versionLog);
    }

    @SuppressWarnings("unchecked")
    private static void applySetCurrentViewVersion(Map<String, Object> update,
            Map<String, Object> metadata) {
        int versionId = toInt(update.get("view-version-id"));
        // The SDK sends -1 as a placeholder for the most recently added version
        if (versionId == -1) {
            List<Object> versions = (List<Object>) metadata.get("versions");
            if (versions != null && !versions.isEmpty()) {
                Map<String, Object> lastVersion = (Map<String, Object>) versions.get(versions.size() - 1);
                versionId = toInt(lastVersion.get("version-id"));
            }
        }
        metadata.put("current-version-id", versionId);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> getMutableList(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) {
            return new ArrayList<>();
        }
        if (value instanceof List) {
            return new ArrayList<>((List<Object>) value);
        }
        throw new IllegalArgumentException(
                "Expected a list for metadata field '" + key + "' but got: " + value.getClass().getName());
    }

    private static int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected a numeric value but got: " + value, e);
        }
    }
}
