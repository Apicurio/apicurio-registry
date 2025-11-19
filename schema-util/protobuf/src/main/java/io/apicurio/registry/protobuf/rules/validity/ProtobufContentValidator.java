package io.apicurio.registry.protobuf.rules.validity;

import com.google.protobuf.Descriptors;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchemaUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A content validator implementation for the Protobuf content type.
 */
public class ProtobufContentValidator implements ContentValidator {

    /**
     * Constructor.
     */
    public ProtobufContentValidator() {
    }

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validate(ValidityLevel, TypedContent, Map)
     */
    @Override
    public void validate(ValidityLevel level, TypedContent content,
                         Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
        if (level == ValidityLevel.SYNTAX_ONLY || level == ValidityLevel.FULL) {
            try {
                if (resolvedReferences == null || resolvedReferences.isEmpty()) {
                    // Simple validation - just try to parse the proto file
                    new ProtobufFile(content.getContent().content());
                }
                else {
                    // Validation with dependencies - use protobuf4j to compile
                    final Map<String, String> dependencies = resolvedReferences.entrySet().stream().collect(
                            Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getContent().content()));

                    // Use protobuf4j to parse and compile - this validates syntax
                    ProtobufSchemaUtils.parseAndCompile("schema.proto",
                            content.getContent().content(), dependencies);
                }
            }
            catch (Exception e) {
                throw new RuleViolationException("Syntax violation for Protobuf artifact.", RuleType.VALIDITY,
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
        try {
            Set<String> mappedRefs = references.stream().map(ref -> ref.getName())
                    .collect(Collectors.toSet());

            // Compile the proto to get FileDescriptor, then get dependencies from it
            Descriptors.FileDescriptor fileDescriptor = ProtobufSchemaUtils.parseAndCompile(
                    "schema.proto", content.getContent().content(), Map.of());

            // Get all imports from FileDescriptor
            Set<String> allImports = fileDescriptor.getDependencies().stream()
                    .map(Descriptors.FileDescriptor::getName)
                    .collect(Collectors.toSet());

            Set<RuleViolation> violations = allImports.stream()
                    .filter(_import -> !mappedRefs.contains(_import))
                    .map(missingRef -> new RuleViolation("Unmapped reference detected.", missingRef))
                    .collect(Collectors.toSet());
            if (!violations.isEmpty()) {
                throw new RuleViolationException("Unmapped reference(s) detected.", RuleType.INTEGRITY,
                        IntegrityLevel.ALL_REFS_MAPPED.name(), violations);
            }
        }
        catch (RuleViolationException rve) {
            throw rve;
        }
        catch (Exception e) {
            // Do nothing - we don't care if it can't validate. Another rule will handle that.
        }
    }
}
