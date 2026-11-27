package io.apicurio.registry.serde.jsonschema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.ParsedSchemaImpl;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class JsonSchemaParser<T> implements SchemaParser<JsonSchema, T> {

    private static final String DRAFT_07_SCHEMA_URI = "http://json-schema.org/draft-07/schema#";

    private final Supplier<ObjectMapper> mapperSupplier;

    public JsonSchemaParser() {
        this(ObjectMapper::new);
    }

    public JsonSchemaParser(Supplier<ObjectMapper> mapperSupplier) {
        this.mapperSupplier = mapperSupplier != null ? mapperSupplier : ObjectMapper::new;
    }

    /**
     * @see io.apicurio.registry.resolver.SchemaParser#artifactType()
     */
    @Override
    public String artifactType() {
        return ArtifactType.JSON;
    }

    /**
     * @see io.apicurio.registry.resolver.SchemaParser#parseSchema(byte[], Map)
     */
    @Override
    public JsonSchema parseSchema(byte[] rawSchema,
            Map<String, ParsedSchema<JsonSchema>> resolvedReferences) {
        Map<String, String> referenceSchemas = new HashMap<>();

        resolveReferences(resolvedReferences, referenceSchemas);

        JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7,
                builder -> builder.schemaLoaders(schemaLoaders -> schemaLoaders.schemas(referenceSchemas)));

        return schemaFactory.getSchema(IoUtil.toString(rawSchema));
    }

    private void resolveReferences(Map<String, ParsedSchema<JsonSchema>> resolvedReferences,
            Map<String, String> referenceSchemas) {
        resolvedReferences.forEach((referenceName, schema) -> {
            if (schema.hasReferences()) {
                resolveReferences(schema.getSchemaReferences().stream()
                        .collect(Collectors.toMap(parsedSchema -> parsedSchema.getParsedSchema().getId(),
                                parsedSchema -> parsedSchema)),
                        referenceSchemas);

            }
            referenceSchemas.put(schema.getParsedSchema().getId(), IoUtil.toString(schema.getRawSchema()));
        });
    }

    /**
     * @see io.apicurio.registry.resolver.SchemaParser#getSchemaFromData(Record)
     */
    @Override
    public ParsedSchema<JsonSchema> getSchemaFromData(Record<T> data) {
        if (data == null || data.payload() == null) {
            return null;
        }
        T payload = data.payload();
        ObjectMapper mapper = mapperSupplier.get();
        JsonNode rootNode = (payload instanceof JsonNode) ? (JsonNode) payload : mapper.valueToTree(payload);
        ObjectNode schemaNode = mapper.createObjectNode();
        schemaNode.put("$schema", DRAFT_07_SCHEMA_URI);
        populateSchemaNode(mapper, schemaNode, rootNode);

        byte[] rawSchema = IoUtil.toBytes(schemaNode.toString());
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        JsonSchema parsedSchema = factory.getSchema(schemaNode);
        return new ParsedSchemaImpl<JsonSchema>()
                .setParsedSchema(parsedSchema)
                .setRawSchema(rawSchema);
    }

    @Override
    public ParsedSchema<JsonSchema> getSchemaFromData(Record<T> data, boolean dereference) {
        return getSchemaFromData(data);
    }

    private void populateSchemaNode(ObjectMapper mapper, ObjectNode targetSchema, JsonNode valueNode) {
        if (valueNode == null || valueNode.isNull() || valueNode.isMissingNode()) {
            return;
        }
        if (valueNode.isObject()) {
            targetSchema.put("type", "object");
            ObjectNode properties = mapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = valueNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                ObjectNode propSchema = mapper.createObjectNode();
                populateSchemaNode(mapper, propSchema, entry.getValue());
                properties.set(entry.getKey(), propSchema);
            }
            if (!properties.isEmpty()) {
                targetSchema.set("properties", properties);
            }
        } else if (valueNode.isArray()) {
            targetSchema.put("type", "array");
            if (!valueNode.isEmpty()) {
                Set<String> itemTypes = new LinkedHashSet<>();
                for (JsonNode element : valueNode) {
                    String elemType = scalarOrContainerType(element);
                    if (elemType != null) {
                        itemTypes.add(elemType);
                    }
                }
                if (itemTypes.size() == 1) {
                    ObjectNode itemsSchema = mapper.createObjectNode();
                    populateSchemaNode(mapper, itemsSchema, valueNode.get(0));
                    targetSchema.set("items", itemsSchema);
                } else if (itemTypes.size() > 1) {
                    ObjectNode itemsSchema = mapper.createObjectNode();
                    ArrayNode typesArray = mapper.createArrayNode();
                    itemTypes.forEach(typesArray::add);
                    itemsSchema.set("type", typesArray);
                    targetSchema.set("items", itemsSchema);
                }
            }
        } else if (valueNode.isTextual() || valueNode.isBinary()) {
            targetSchema.put("type", "string");
        } else if (valueNode.isIntegralNumber()) {
            targetSchema.put("type", "integer");
        } else if (valueNode.isNumber()) {
            targetSchema.put("type", "number");
        } else if (valueNode.isBoolean()) {
            targetSchema.put("type", "boolean");
        }
    }

    private String scalarOrContainerType(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isObject()) {
            return "object";
        }
        if (node.isArray()) {
            return "array";
        }
        if (node.isTextual() || node.isBinary()) {
            return "string";
        }
        if (node.isIntegralNumber()) {
            return "integer";
        }
        if (node.isNumber()) {
            return "number";
        }
        if (node.isBoolean()) {
            return "boolean";
        }
        return null;
    }

    @Override
    public ParsedSchema<JsonSchema> getSchemaFromLocation(String location) {
        String rawSchema = IoUtil
                .toString(Thread.currentThread().getContextClassLoader().getResourceAsStream(location));
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        return new ParsedSchemaImpl<JsonSchema>()
                .setParsedSchema(factory.getSchema(IoUtil.toStream(rawSchema)))
                .setRawSchema(rawSchema.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean supportsExtractSchemaFromData() {
        return true;
    }

    @Override
    public boolean supportsGetSchemaFromLocation() {
        return true;
    }
}
