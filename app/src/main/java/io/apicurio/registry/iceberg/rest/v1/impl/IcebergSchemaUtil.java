package io.apicurio.registry.iceberg.rest.v1.impl;

import java.util.List;
import java.util.Map;

public final class IcebergSchemaUtil {

    private IcebergSchemaUtil() {
    }

    @SuppressWarnings("unchecked")
    public static int computeMaxFieldId(Map<String, Object> schema) {
        List<Map<String, Object>> fields = (List<Map<String, Object>>) schema.get("fields");
        if (fields == null || fields.isEmpty()) {
            return 0;
        }
        int maxId = 0;
        for (Map<String, Object> field : fields) {
            maxId = Math.max(maxId, computeMaxFieldIdFromField(field));
        }
        return maxId;
    }

    @SuppressWarnings("unchecked")
    private static int computeMaxFieldIdFromField(Map<String, Object> field) {
        int maxId = toInt(field.get("id"));
        Object type = field.get("type");
        if (type instanceof Map) {
            maxId = Math.max(maxId, computeMaxFieldIdFromType((Map<String, Object>) type));
        }
        return maxId;
    }

    @SuppressWarnings("unchecked")
    private static int computeMaxFieldIdFromType(Map<String, Object> type) {
        String typeName = (String) type.get("type");
        if (typeName == null) {
            return 0;
        }
        int maxId = 0;
        switch (typeName) {
            case "struct":
                List<Map<String, Object>> fields = (List<Map<String, Object>>) type.get("fields");
                if (fields != null) {
                    for (Map<String, Object> field : fields) {
                        maxId = Math.max(maxId, computeMaxFieldIdFromField(field));
                    }
                }
                break;
            case "list":
                maxId = Math.max(maxId, toInt(type.get("element-id")));
                Object element = type.get("element");
                if (element instanceof Map) {
                    maxId = Math.max(maxId, computeMaxFieldIdFromType((Map<String, Object>) element));
                }
                break;
            case "map":
                maxId = Math.max(maxId, toInt(type.get("key-id")));
                maxId = Math.max(maxId, toInt(type.get("value-id")));
                Object key = type.get("key");
                if (key instanceof Map) {
                    maxId = Math.max(maxId, computeMaxFieldIdFromType((Map<String, Object>) key));
                }
                Object value = type.get("value");
                if (value instanceof Map) {
                    maxId = Math.max(maxId, computeMaxFieldIdFromType((Map<String, Object>) value));
                }
                break;
            default:
                break;
        }
        return maxId;
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
            return 0;
        }
    }
}
