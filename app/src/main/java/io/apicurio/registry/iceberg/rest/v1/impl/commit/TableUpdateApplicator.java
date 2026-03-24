package io.apicurio.registry.iceberg.rest.v1.impl.commit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies Iceberg table updates to a mutable metadata map. All updates within a single commit are applied
 * sequentially to produce one new metadata state.
 */
public final class TableUpdateApplicator {

    private TableUpdateApplicator() {
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
                    metadata.put("table-uuid", update.get("uuid"));
                    break;
                case "upgrade-format-version":
                    applyUpgradeFormatVersion(update, metadata);
                    break;
                case "add-schema":
                    applyAddSchema(update, metadata);
                    break;
                case "set-current-schema":
                    applySetCurrentSchema(update, metadata);
                    break;
                case "add-spec":
                    applyAddSpec(update, metadata);
                    break;
                case "set-default-spec":
                    applySetDefaultSpec(update, metadata);
                    break;
                case "add-sort-order":
                    applyAddSortOrder(update, metadata);
                    break;
                case "set-default-sort-order":
                    applySetDefaultSortOrder(update, metadata);
                    break;
                case "add-snapshot":
                    applyAddSnapshot(update, metadata);
                    break;
                case "set-snapshot-ref":
                    applySetSnapshotRef(update, metadata);
                    break;
                case "remove-snapshots":
                    applyRemoveSnapshots(update, metadata);
                    break;
                case "remove-snapshot-ref":
                    applyRemoveSnapshotRef(update, metadata);
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
                default:
                    throw new IllegalArgumentException("Unknown update action: " + action);
            }
        }

        metadata.put("last-updated-ms", System.currentTimeMillis());
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

        // Compute next schema-id: max existing + 1
        int nextSchemaId = 0;
        for (Object s : schemas) {
            if (s instanceof Map) {
                int sid = toInt(((Map<String, Object>) s).get("schema-id"));
                if (sid >= nextSchemaId) {
                    nextSchemaId = sid + 1;
                }
            }
        }

        // Auto-assign schema-id if not present or if -1 (sentinel)
        int schemaId;
        if (!schema.containsKey("schema-id") || toInt(schema.get("schema-id")) < 0) {
            schemaId = nextSchemaId;
            schema = new HashMap<>(schema);
            schema.put("schema-id", schemaId);
        } else {
            schemaId = toInt(schema.get("schema-id"));
        }

        schemas.add(schema);
        metadata.put("schemas", schemas);

        // Update last-column-id from the update's last-column-id or from field IDs
        if (update.containsKey("last-column-id")) {
            int updateLastColumnId = toInt(update.get("last-column-id"));
            int currentLastColumnId = toInt(metadata.getOrDefault("last-column-id", 0));
            if (updateLastColumnId > currentLastColumnId) {
                metadata.put("last-column-id", updateLastColumnId);
            }
        } else {
            updateLastColumnId(schema, metadata);
        }
    }

    @SuppressWarnings("unchecked")
    private static void updateLastColumnId(Map<String, Object> schema, Map<String, Object> metadata) {
        List<Map<String, Object>> fields = (List<Map<String, Object>>) schema.get("fields");
        if (fields != null) {
            int maxFieldId = toInt(metadata.getOrDefault("last-column-id", 0));
            for (Map<String, Object> field : fields) {
                int fieldId = toInt(field.get("id"));
                if (fieldId > maxFieldId) {
                    maxFieldId = fieldId;
                }
            }
            metadata.put("last-column-id", maxFieldId);
        }
    }

    @SuppressWarnings("unchecked")
    private static void applySetCurrentSchema(Map<String, Object> update, Map<String, Object> metadata) {
        int schemaId = toInt(update.get("schema-id"));
        if (schemaId == -1) {
            // Sentinel: resolve to the last schema in the list
            List<Object> schemas = getMutableList(metadata, "schemas");
            if (!schemas.isEmpty()) {
                Object lastSchema = schemas.get(schemas.size() - 1);
                if (lastSchema instanceof Map) {
                    schemaId = toInt(((Map<String, Object>) lastSchema).get("schema-id"));
                }
            }
        }
        metadata.put("current-schema-id", schemaId);
    }

    @SuppressWarnings("unchecked")
    private static void applySetDefaultSpec(Map<String, Object> update, Map<String, Object> metadata) {
        int specId = toInt(update.get("spec-id"));
        if (specId == -1) {
            // Sentinel: resolve to the last partition spec in the list
            List<Object> specs = getMutableList(metadata, "partition-specs");
            if (!specs.isEmpty()) {
                Object lastSpec = specs.get(specs.size() - 1);
                if (lastSpec instanceof Map) {
                    specId = toInt(((Map<String, Object>) lastSpec).get("spec-id"));
                }
            }
        }
        metadata.put("default-spec-id", specId);
    }

    @SuppressWarnings("unchecked")
    private static void applySetDefaultSortOrder(Map<String, Object> update, Map<String, Object> metadata) {
        int orderId = toInt(update.get("order-id"));
        if (orderId == -1) {
            // Sentinel: resolve to the last sort order in the list
            List<Object> sortOrders = getMutableList(metadata, "sort-orders");
            if (!sortOrders.isEmpty()) {
                Object lastOrder = sortOrders.get(sortOrders.size() - 1);
                if (lastOrder instanceof Map) {
                    orderId = toInt(((Map<String, Object>) lastOrder).get("order-id"));
                }
            }
        }
        metadata.put("default-sort-order-id", orderId);
    }

    @SuppressWarnings("unchecked")
    private static void applyAddSpec(Map<String, Object> update, Map<String, Object> metadata) {
        Map<String, Object> spec = (Map<String, Object>) update.get("spec");
        if (spec == null) {
            throw new IllegalArgumentException("add-spec update is missing 'spec' field");
        }

        List<Object> specs = getMutableList(metadata, "partition-specs");

        // Auto-assign spec-id if -1 (sentinel) or not present
        if (!spec.containsKey("spec-id") || toInt(spec.get("spec-id")) < 0) {
            int nextSpecId = 0;
            for (Object s : specs) {
                if (s instanceof Map) {
                    int sid = toInt(((Map<String, Object>) s).get("spec-id"));
                    if (sid >= nextSpecId) {
                        nextSpecId = sid + 1;
                    }
                }
            }
            spec = new HashMap<>(spec);
            spec.put("spec-id", nextSpecId);
        }

        specs.add(spec);
        metadata.put("partition-specs", specs);

        // Update last-partition-id
        List<Map<String, Object>> fields = (List<Map<String, Object>>) spec.get("fields");
        if (fields != null) {
            int maxPartId = toInt(metadata.getOrDefault("last-partition-id", 999));
            for (Map<String, Object> field : fields) {
                if (field.containsKey("field-id")) {
                    int fieldId = toInt(field.get("field-id"));
                    if (fieldId > maxPartId) {
                        maxPartId = fieldId;
                    }
                }
            }
            metadata.put("last-partition-id", maxPartId);
        }
    }

    @SuppressWarnings("unchecked")
    private static void applyAddSortOrder(Map<String, Object> update, Map<String, Object> metadata) {
        Map<String, Object> sortOrder = (Map<String, Object>) update.get("sort-order");
        if (sortOrder == null) {
            throw new IllegalArgumentException("add-sort-order update is missing 'sort-order' field");
        }

        List<Object> sortOrders = getMutableList(metadata, "sort-orders");
        sortOrders.add(sortOrder);
        metadata.put("sort-orders", sortOrders);
    }

    @SuppressWarnings("unchecked")
    private static void applyAddSnapshot(Map<String, Object> update, Map<String, Object> metadata) {
        Map<String, Object> snapshot = (Map<String, Object>) update.get("snapshot");
        if (snapshot == null) {
            throw new IllegalArgumentException("add-snapshot update is missing 'snapshot' field");
        }

        List<Object> snapshots = getMutableList(metadata, "snapshots");
        snapshots.add(snapshot);
        metadata.put("snapshots", snapshots);

        // Update snapshot-log
        List<Object> snapshotLog = getMutableList(metadata, "snapshot-log");
        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("snapshot-id", snapshot.get("snapshot-id"));
        logEntry.put("timestamp-ms", snapshot.get("timestamp-ms"));
        snapshotLog.add(logEntry);
        metadata.put("snapshot-log", snapshotLog);

        // Increment last-sequence-number
        long seqNum = toLong(metadata.getOrDefault("last-sequence-number", 0L));
        metadata.put("last-sequence-number", seqNum + 1);
    }

    @SuppressWarnings("unchecked")
    private static void applySetSnapshotRef(Map<String, Object> update, Map<String, Object> metadata) {
        String refName = (String) update.get("ref-name");
        if (refName == null) {
            throw new IllegalArgumentException("set-snapshot-ref update is missing 'ref-name' field");
        }

        Map<String, Object> refs = (Map<String, Object>) metadata.get("refs");
        if (refs == null) {
            refs = new HashMap<>();
        } else {
            refs = new HashMap<>(refs);
        }

        Map<String, Object> refData = new HashMap<>();
        refData.put("snapshot-id", update.get("snapshot-id"));
        refData.put("type", update.getOrDefault("type", "branch"));
        if (update.containsKey("max-ref-age-ms")) {
            refData.put("max-ref-age-ms", update.get("max-ref-age-ms"));
        }
        if (update.containsKey("max-snapshot-age-ms")) {
            refData.put("max-snapshot-age-ms", update.get("max-snapshot-age-ms"));
        }
        if (update.containsKey("min-snapshots-to-keep")) {
            refData.put("min-snapshots-to-keep", update.get("min-snapshots-to-keep"));
        }

        refs.put(refName, refData);
        metadata.put("refs", refs);

        // If the ref is "main", also set current-snapshot-id
        if ("main".equals(refName)) {
            metadata.put("current-snapshot-id", update.get("snapshot-id"));
        }
    }

    @SuppressWarnings("unchecked")
    private static void applyRemoveSnapshots(Map<String, Object> update, Map<String, Object> metadata) {
        List<Object> snapshotIdsToRemove = (List<Object>) update.get("snapshot-ids");
        if (snapshotIdsToRemove == null || snapshotIdsToRemove.isEmpty()) {
            return;
        }

        List<Object> snapshots = getMutableList(metadata, "snapshots");
        List<Long> removeIds = new ArrayList<>();
        for (Object id : snapshotIdsToRemove) {
            removeIds.add(toLong(id));
        }

        snapshots.removeIf(s -> {
            if (s instanceof Map) {
                Long snapshotId = toLong(((Map<String, Object>) s).get("snapshot-id"));
                return removeIds.contains(snapshotId);
            }
            return false;
        });
        metadata.put("snapshots", snapshots);
    }

    @SuppressWarnings("unchecked")
    private static void applyRemoveSnapshotRef(Map<String, Object> update, Map<String, Object> metadata) {
        String refName = (String) update.get("ref-name");
        if (refName == null) {
            return;
        }

        Map<String, Object> refs = (Map<String, Object>) metadata.get("refs");
        if (refs != null) {
            refs = new HashMap<>(refs);
            refs.remove(refName);
            metadata.put("refs", refs);
        }
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

    private static long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected a numeric value but got: " + value, e);
        }
    }
}
