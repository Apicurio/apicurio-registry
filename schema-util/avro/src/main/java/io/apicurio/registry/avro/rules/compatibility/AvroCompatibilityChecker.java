package io.apicurio.registry.avro.rules.compatibility;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.compatibility.AbstractCompatibilityChecker;
import io.apicurio.registry.rules.compatibility.SimpleCompatibilityDifference;
import io.apicurio.registry.rules.violation.UnprocessableSchemaException;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AvroCompatibilityChecker extends AbstractCompatibilityChecker<SimpleCompatibilityDifference> {

    @Override
    protected Set<SimpleCompatibilityDifference> isBackwardsCompatibleWith(String existing, String proposed,
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
                    return result.getIncompatibilities().stream()
                            .map(incompatibility -> new SimpleCompatibilityDifference(
                                    incompatibility.getMessage(), incompatibility.getLocation()))
                            .collect(Collectors.toSet());
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
}
