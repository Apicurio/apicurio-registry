package io.apicurio.registry.rules.compatibility;

import com.google.common.collect.ImmutableSet;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.UnprocessableSchemaException;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.SchemaCompatibility.Incompatibility;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class AvroCompatibilityChecker extends AbstractCompatibilityChecker<Incompatibility> {

    @Override
    protected Set<Incompatibility> isBackwardsCompatibleWith(String existing, String proposed,
            Map<String, TypedContent> resolvedReferences) {
        try {
            Schema.Parser existingParser = new Schema.Parser();
            for (TypedContent schema : resolvedReferences.values()) {
                existingParser.parse(schema.getContent().content());
            }
            final Schema existingSchema = existingParser.parse(existing);

            Schema.Parser proposingParser = new Schema.Parser();
            for (TypedContent schema : resolvedReferences.values()) {
                proposingParser.parse(schema.getContent().content());
            }
            final Schema proposedSchema = proposingParser.parse(proposed);

            var result = SchemaCompatibility.checkReaderWriterCompatibility(proposedSchema, existingSchema)
                    .getResult();
            switch (result.getCompatibility()) {
                case COMPATIBLE:
                    return Collections.emptySet();
                case INCOMPATIBLE: {
                    return ImmutableSet.<Incompatibility> builder().addAll(result.getIncompatibilities())
                            .build();
                }
                default:
                    throw new IllegalStateException(
                            "Got illegal compatibility result: " + result.getCompatibility());
            }
        } catch (Exception ex) {
            throw new UnprocessableSchemaException(
                    "Could not execute compatibility rule on invalid Avro schema", ex);
        }
    }

    @Override
    protected CompatibilityDifference transform(Incompatibility original) {
        return new SimpleCompatibilityDifference(original.getMessage(), original.getLocation());
    }
}
