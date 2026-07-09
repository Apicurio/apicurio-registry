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
            int fieldId = toInt(field.get("id"));
            if (fieldId > maxId) {
                maxId = fieldId;
            }
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
