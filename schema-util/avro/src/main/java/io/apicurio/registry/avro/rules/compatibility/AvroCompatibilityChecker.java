package io.apicurio.registry.avro.rules.compatibility;

import io.apicurio.registry.avro.util.AvroParserAccessor;
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
            // Two independent parsers: each accumulates the named types it parses, so reusing one would
            // leak the existing schema's types into the proposed schema's namespace.
            final Schema existingSchema = AvroParserAccessor.newParser(resolvedReferences).parse(existing);
            final Schema proposedSchema = AvroParserAccessor.newParser(resolvedReferences).parse(proposed);

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
