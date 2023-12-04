package io.apicurio.registry.content.canon;

import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.apicurio.registry.content.ContentHandle;

/**
 * An Avro implementation of a content Canonicalizer that handles avro references.
 * A custom version that can be used to check subject compatibilities. It does not reorder fields.
 */
public class EnhancedAvroContentCanonicalizer implements ContentCanonicalizer {

    public static final String EMPTY_DOC = "";

    public static Schema normalizeSchema(String schemaString) {
        Schema.Parser parser = new Schema.Parser();
        Schema schema = parser.parse(schemaString);

        return normalizeSchema(schema);
    }

    public static Schema normalizeSchema(Schema schema) {
        return normalizeSchema(schema, new HashMap<>());
    }

    /**
     * Normalize a schema.
     *
     * @param schema            a schema.
     * @param alreadyNormalized a Map indicating if the fields in the schema were already normalized.
     * @return the same schema functionally, in normalized form.
     */
    private static Schema normalizeSchema(Schema schema, Map<String, Boolean> alreadyNormalized) {

        // if it's a nested type RECORD, check if it was already normalized and update our administration
        if (schema.getType().equals(Schema.Type.RECORD)) {
            String key = createKey(schema);
            if (alreadyNormalized.containsKey(key)) {
                // don't normalize again
                return schema;
            } else {
                alreadyNormalized.put(key, true);
            }
        }

        final Schema result;
        switch (schema.getType()) {
            case RECORD:
                result = Schema.createRecord(schema.getName(), EMPTY_DOC, schema.getNamespace(), false, normalizeFields(schema.getFields(), alreadyNormalized));
                break;
            case ENUM:
                result = Schema.createEnum(schema.getName(), EMPTY_DOC, schema.getNamespace(), schema.getEnumSymbols());
                break;
            case ARRAY:
                result = Schema.createArray(normalizeSchema(schema.getElementType(), alreadyNormalized));
                break;
            case FIXED:
                result = Schema.createFixed(schema.getName(), EMPTY_DOC, schema.getNamespace(), schema.getFixedSize());
                break;
            case UNION:
                result = Schema.createUnion(normalizeSchemasList(schema.getTypes(), alreadyNormalized));
                break;
            case MAP:
                result = Schema.createMap(normalizeSchema(schema.getValueType()));
                break;
            default:
                result = Schema.create(schema.getType());
        }
        schema.getObjectProps().forEach(result::addProp);
        return result;
    }

    private static List<Schema> normalizeSchemasList(List<Schema> schemas, Map<String, Boolean> alreadyNormalized) {
        final List<Schema> result = new ArrayList<>(schemas.size());
        for (Schema schema : schemas) {
            result.add(normalizeSchema(schema, alreadyNormalized));
        }
        return result;
    }

    private static Schema.Field normalizeField(Schema.Field field, Map<String, Boolean> alreadyNormalized) {
        final Schema.Field result = new Schema.Field(field.name(), normalizeSchema(field.schema(), alreadyNormalized), EMPTY_DOC, field.defaultVal(), field.order());
        field.getObjectProps().forEach(result::addProp);
        return result;
    }

    private static List<Schema.Field> normalizeFields(List<Schema.Field> fields, Map<String, Boolean> alreadyNormalized) {
        List<Schema.Field> result = new ArrayList<>(fields.size());
        for (Schema.Field field : fields) {
            result.add(normalizeField(field, alreadyNormalized));
        }
        return result;
    }

    /**
     * Create a key for the internal map.
     *
     * @param schema a schema.
     * @return the schema namespace (if any), concatenated with the schema name.
     */
    private static String createKey(Schema schema) {
        String namespace = "";
        try {
            namespace = schema.getNamespace() == null ? "" : schema.getNamespace();
        } catch (AvroRuntimeException e) {
            // not namespaced, leave as is
        }
        return namespace + ":" + schema.getName();
    }

    /**
     * @see ContentCanonicalizer#canonicalize(ContentHandle, Map)
     */
    @Override
    public ContentHandle canonicalize(ContentHandle content, Map<String, ContentHandle> resolvedReferences) {
        String normalisedSchema = normalizeSchema(content.content()).toString();
        return ContentHandle.create(normalisedSchema);
    }
}
