package io.apicurio.registry.avro.rules.validity;

import io.apicurio.registry.avro.content.refs.AvroReferenceFinder;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import org.apache.avro.Schema;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A content validator implementation for the Avro content type.
 */
public class AvroContentValidator implements ContentValidator {

    /**
     * Constructor.
     */
    public AvroContentValidator() {
    }

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validate(ValidityLevel, TypedContent, Map)
     */
    @Override
    public void validate(ValidityLevel level, TypedContent content,
            Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
        if (level == ValidityLevel.SYNTAX_ONLY || level == ValidityLevel.FULL) {
            try {
                Schema.Parser parser = new Schema.Parser();
                for (TypedContent schemaTC : resolvedReferences.values()) {
                    parser.parse(schemaTC.getContent().content());
                }
                parser.parse(content.getContent().content());
            } catch (Exception e) {
                throw new RuleViolationException("Syntax violation for Avro artifact.", RuleType.VALIDITY,
                        level.name(), e);
            }
        }
    }

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validateReferences(TypedContent, List)
     */
    @Override
    public void validateReferences(TypedContent content, List<ArtifactReference> references)
            throws RuleViolationException {
        // Use pre-validation approach: extract all external type references from the schema
        // and compare them against the mapped reference names.
        // This avoids relying on fragile error message parsing from Avro exceptions.
        AvroReferenceFinder referenceFinder = new AvroReferenceFinder();
        Set<ExternalReference> requiredReferences = referenceFinder.findExternalReferences(content);

        // Collect all mapped reference names
        Set<String> mappedReferenceNames = references.stream()
                .map(ArtifactReference::getName)
                .filter(name -> name != null)
                .collect(Collectors.toSet());

        // Find any missing references (required but not mapped)
        Set<String> missingReferences = requiredReferences.stream()
                .map(ExternalReference::getResource)
                .filter(refName -> !mappedReferenceNames.contains(refName))
                .collect(Collectors.toSet());

        if (!missingReferences.isEmpty()) {
            String missingList = String.join(", ", missingReferences);
            RuleViolation violation = new RuleViolation("Missing reference detected.",
                    "Undefined schema: " + missingList);
            throw new RuleViolationException("Missing reference detected in Avro artifact.",
                    RuleType.INTEGRITY, IntegrityLevel.ALL_REFS_MAPPED.name(),
                    Set.of(violation));
        }
    }

}
