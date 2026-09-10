package io.apicurio.registry.avro.content.canon;

import io.apicurio.registry.avro.util.AvroParserAccessor;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.types.ContentTypes;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.JsonProperties;
import org.apache.avro.Schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An Avro implementation of a content Canonicalizer that handles avro references. A custom version that can
 * be used to check subject compatibilities.
 * <p>
 * Field order is deliberately preserved. Avro treats field ordering as part of a record's identity: it
 * determines the binary encoding layout and interacts with the per-field {@code order} attribute. Two
 * schemas that differ only in the order of their fields are therefore <em>not</em> canonically equal, and
 * canonical-hash lookups (a {@code canonical=true} search, or ccompat's
 * {@code apicurio.ccompat.use-canonical-hash}) will treat them as distinct content. This differs from the
 * general {@link ContentCanonicalizer} contract, which sorts fields when ordering is not significant.
 */
public class EnhancedAvroContentCanonicalizer implements ContentCanonicalizer {

    public static final String EMPTY_DOC = "";

    public static Schema normalizeSchema(String schemaString) {
        return normalizeSchema(schemaString, Map.of());
    }

    /**
     * Normalize a schema, resolving any named types that the schema references but does not declare itself.
     *
     * @param schemaString a schema.
     * @param resolvedReferences the content of the artifacts referenced by the schema, keyed by reference
     *            name. May be empty.
     * @return the same schema functionally, in normalized form.
     */
    public static Schema normalizeSchema(String schemaString,
            Map<String, TypedContent> resolvedReferences) {
        // The parser is seeded with the referenced schemas so that the named types they declare are already
        // known once the main schema is parsed; see AvroParserAccessor#newParser(Map).
        Schema.Parser parser = AvroParserAccessor.newParser(resolvedReferences);

        return normalizeSchema(parser.parse(schemaString));
    }

    public static Schema normalizeSchema(Schema schema) {
        return normalizeSchema(schema, new HashMap<>());
    }

    /**
     * Normalize a schema.
     *
     * @param schema a schema.
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
                result = Schema.createRecord(schema.getName(), EMPTY_DOC, schema.getNamespace(), false,
                        normalizeFields(schema.getFields(), alreadyNormalized));
                break;
            case ENUM:
                result = Schema.createEnum(schema.getName(), EMPTY_DOC, schema.getNamespace(),
                        schema.getEnumSymbols());
                break;
            case ARRAY:
                result = Schema.createArray(normalizeSchema(schema.getElementType(), alreadyNormalized));
                break;
            case FIXED:
                result = Schema.createFixed(schema.getName(), EMPTY_DOC, schema.getNamespace(),
                        schema.getFixedSize());
                break;
            case UNION:
                result = Schema.createUnion(normalizeSchemasList(schema.getTypes(), alreadyNormalized));
                break;
            case MAP:
                result = Schema.createMap(normalizeSchema(schema.getValueType(), alreadyNormalized));
                break;
            default:
                result = Schema.create(schema.getType());
        }
        schema.getObjectProps().forEach(result::addProp);
        return result;
    }

    private static List<Schema> normalizeSchemasList(List<Schema> schemas,
            Map<String, Boolean> alreadyNormalized) {
        final List<Schema> result = new ArrayList<>(schemas.size());
        for (Schema schema : schemas) {
            result.add(normalizeSchema(schema, alreadyNormalized));
        }
        return result;
    }

    private static Schema.Field normalizeField(Schema.Field field, Map<String, Boolean> alreadyNormalized) {
        Object defaultVal = field.defaultVal();
        if (defaultVal == null && field.schema().getType() == Schema.Type.UNION) {
            List<Schema> unionTypes = field.schema().getTypes();
            if (!unionTypes.isEmpty() && unionTypes.get(0).getType() == Schema.Type.NULL) {
                defaultVal = JsonProperties.NULL_VALUE;
            }
        }
        final Schema.Field result = new Schema.Field(field.name(),
                normalizeSchema(field.schema(), alreadyNormalized), EMPTY_DOC, defaultVal,
                field.order());
        field.getObjectProps().forEach(result::addProp);
        return result;
    }

    private static List<Schema.Field> normalizeFields(List<Schema.Field> fields,
            Map<String, Boolean> alreadyNormalized) {
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
     * @see ContentCanonicalizer#canonicalize(TypedContent, Map)
     */
    @Override
    public TypedContent canonicalize(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        String normalisedSchema = normalizeSchema(content.getContent().content(), resolvedReferences)
                .toString();
        return TypedContent.create(ContentHandle.create(normalisedSchema), ContentTypes.APPLICATION_JSON);
    }
}
