package io.apicurio.registry.flink.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.catalog.Column;
import org.apache.flink.table.catalog.ResolvedSchema;
import org.apache.flink.table.types.DataType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Converts JSON Schema to Flink DataTypes.
 */
public final class JsonSchemaFlinkTypeConverter {

    /** JSON object mapper instance. */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Precision for time types. */
    private static final int TIME_PRECISION = 3;

    private JsonSchemaFlinkTypeConverter() {
    }

    /**
     * Converts a JSON Schema string to a Flink ResolvedSchema.
     *
     * @param jsonSchemaString the JSON Schema as string
     * @return the Flink ResolvedSchema
     */
    public static ResolvedSchema convert(final String jsonSchemaString) {
        try {
            final JsonNode schemaNode =
                    OBJECT_MAPPER.readTree(jsonSchemaString);
            return convertObject(schemaNode);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON Schema", e);
        }
    }

    private static ResolvedSchema convertObject(final JsonNode schemaNode) {
        final String type = getType(schemaNode);
        if (!"object".equals(type)) {
            throw new IllegalArgumentException(
                    "Expected 'object' type at root, got: " + type);
        }
        final JsonNode propertiesNode = schemaNode.get("properties");
        if (propertiesNode == null) {
            return ResolvedSchema.of(new ArrayList<>());
        }
        final List<String> requiredFields = getRequiredFields(schemaNode);
        final List<Column> columns = new ArrayList<>();
        final Iterator<Map.Entry<String, JsonNode>> fields =
                propertiesNode.fields();

        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> field = fields.next();
            final String fieldName = field.getKey();
            final JsonNode fieldSchema = field.getValue();
            DataType dataType = convertType(fieldSchema);
            if (!requiredFields.contains(fieldName)) {
                dataType = dataType.nullable();
            }
            columns.add(Column.physical(fieldName, dataType));
        }
        return ResolvedSchema.of(columns);
    }

    private static List<String> getRequiredFields(final JsonNode schemaNode) {
        final List<String> requiredFields = new ArrayList<>();
        final JsonNode requiredNode = schemaNode.get("required");
        if (requiredNode != null && requiredNode.isArray()) {
            for (JsonNode req : requiredNode) {
                requiredFields.add(req.asText());
            }
        }
        return requiredFields;
    }

    /**
     * Converts a JSON Schema node to a Flink DataType.
     *
     * @param schemaNode the JSON Schema node
     * @return the Flink DataType
     */
    public static DataType convertType(final JsonNode schemaNode) {
        if (schemaNode.has("$ref")) {
            return DataTypes.STRING();
        }
        if (schemaNode.has("oneOf") || schemaNode.has("anyOf")) {
            final JsonNode unionNode = schemaNode.has("oneOf")
                    ? schemaNode.get("oneOf")
                    : schemaNode.get("anyOf");
            return convertUnion(unionNode);
        }
        final String type = getType(schemaNode);
        final String format = schemaNode.has("format")
                ? schemaNode.get("format").asText()
                : null;
        return convertByType(schemaNode, type, format);
    }

    private static DataType convertByType(
            final JsonNode schemaNode,
            final String type,
            final String format) {
        switch (type) {
            case "null":
                return DataTypes.NULL();
            case "boolean":
                return DataTypes.BOOLEAN();
            case "integer":
                return "int64".equals(format)
                        ? DataTypes.BIGINT()
                        : DataTypes.INT();
            case "number":
                return "float".equals(format)
                        ? DataTypes.FLOAT()
                        : DataTypes.DOUBLE();
            case "string":
                return convertStringType(format);
            case "array":
                return convertArrayType(schemaNode);
            case "object":
                return convertObjectToRow(schemaNode);
            default:
                return DataTypes.STRING();
        }
    }

    private static String getType(final JsonNode schemaNode) {
        final JsonNode typeNode = schemaNode.get("type");
        if (typeNode == null) {
            return "object";
        }
        if (typeNode.isArray()) {
            for (JsonNode t : typeNode) {
                if (!"null".equals(t.asText())) {
                    return t.asText();
                }
            }
            return "null";
        }
        return typeNode.asText();
    }

    private static DataType convertStringType(final String format) {
        if (format == null) {
            return DataTypes.STRING();
        }
        switch (format) {
            case "date":
                return DataTypes.DATE();
            case "time":
                return DataTypes.TIME(TIME_PRECISION);
            case "date-time":
                return DataTypes.TIMESTAMP(TIME_PRECISION);
            case "binary":
            case "byte":
                return DataTypes.BYTES();
            default:
                return DataTypes.STRING();
        }
    }

    private static DataType convertArrayType(final JsonNode schemaNode) {
        final JsonNode itemsNode = schemaNode.get("items");
        if (itemsNode == null) {
            return DataTypes.ARRAY(DataTypes.STRING());
        }
        return DataTypes.ARRAY(convertType(itemsNode));
    }

    private static DataType convertObjectToRow(final JsonNode schemaNode) {
        final JsonNode propertiesNode = schemaNode.get("properties");
        if (propertiesNode == null) {
            final JsonNode additionalProps =
                    schemaNode.get("additionalProperties");
            if (additionalProps != null && additionalProps.isObject()) {
                return DataTypes.MAP(
                        DataTypes.STRING(),
                        convertType(additionalProps));
            }
            return DataTypes.MAP(
                    DataTypes.STRING(),
                    DataTypes.STRING());
        }

        final List<String> requiredFields = getRequiredFields(schemaNode);
        final List<DataTypes.Field> fields = new ArrayList<>();
        final Iterator<Map.Entry<String, JsonNode>> properties =
                propertiesNode.fields();

        while (properties.hasNext()) {
            final Map.Entry<String, JsonNode> property = properties.next();
            final String fieldName = property.getKey();
            final JsonNode fieldSchema = property.getValue();
            DataType fieldType = convertType(fieldSchema);
            if (!requiredFields.contains(fieldName)) {
                fieldType = fieldType.nullable();
            }
            fields.add(DataTypes.FIELD(fieldName, fieldType));
        }
        return DataTypes.ROW(fields.toArray(new DataTypes.Field[0]));
    }

    private static DataType convertUnion(final JsonNode unionNode) {
        final List<DataType> nonNullTypes = new ArrayList<>();
        boolean hasNull = false;

        for (JsonNode typeNode : unionNode) {
            final String type = getType(typeNode);
            if ("null".equals(type)) {
                hasNull = true;
            } else {
                nonNullTypes.add(convertType(typeNode));
            }
        }

        if (nonNullTypes.isEmpty()) {
            return DataTypes.NULL();
        }
        if (nonNullTypes.size() == 1) {
            final DataType dataType = nonNullTypes.get(0);
            return hasNull ? dataType.nullable() : dataType.notNull();
        }
        return DataTypes.STRING();
    }
}
