package io.apicurio.registry.avro.content.canon;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.types.ContentTypes;
import org.apache.avro.Schema;
import org.apache.avro.SchemaNormalization;

import java.util.Map;

/**
 * An Avro implementation of a content Canonicalizer that handles avro references. Uses Apache Avro's standard
 * {@link SchemaNormalization#toParsingForm(Schema)} for canonical form.
 */
public class AvroContentCanonicalizer implements ContentCanonicalizer {

    public static Schema normalizeSchema(String schemaString,
            Map<String, TypedContent> resolvedReferences) {
        final Schema.Parser parser = new Schema.Parser();
        for (TypedContent referencedContent : resolvedReferences.values()) {
            parser.parse(referencedContent.getContent().content());
        }
        final Schema schema = parser.parse(schemaString);
        return normalizeSchema(schema);
    }

    public static Schema normalizeSchema(Schema schema) {
        return new Schema.Parser().parse(SchemaNormalization.toParsingForm(schema));
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
