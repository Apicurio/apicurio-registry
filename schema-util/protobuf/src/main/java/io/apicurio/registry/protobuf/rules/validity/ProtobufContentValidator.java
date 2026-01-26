package io.apicurio.registry.protobuf.rules.validity;

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
        String schemaContent = content.getContent().content();

        if (level == ValidityLevel.SYNTAX_ONLY) {
            // For SYNTAX_ONLY, just validate syntax without resolving imports
            try {
                // Check if this is a base64-encoded binary descriptor
                if (ProtobufSchemaUtils.isBase64BinaryDescriptor(schemaContent)) {
                    // Binary descriptors are pre-compiled, so we just validate they parse correctly
                    ProtobufSchemaUtils.validateBinaryDescriptorSyntax(schemaContent);
                } else {
                    // Text format - use standard syntax validation
                    ProtobufFile.validateSyntaxOnly(schemaContent);
                }
            }
            catch (Exception e) {
                throw new RuleViolationException("Syntax violation for Protobuf artifact.", RuleType.VALIDITY,
                        level.name(), e);
            }
        }
        else if (level == ValidityLevel.FULL) {
            // For FULL validation, parse and compile with dependencies
            try {
                // Check if this is a base64-encoded binary descriptor
                boolean isBinaryDescriptor = ProtobufSchemaUtils.isBase64BinaryDescriptor(schemaContent);

                if (resolvedReferences == null || resolvedReferences.isEmpty()) {
                    // Simple validation - parse the proto file (handles both text and binary)
                    new ProtobufFile(schemaContent);
                }
                else {
                    // Validation with dependencies
                    final Map<String, String> dependencies = resolvedReferences.entrySet().stream().collect(
                            Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getContent().content()));

                    if (isBinaryDescriptor) {
                        // Binary descriptor with dependencies
                        ProtobufSchemaUtils.parseBase64BinaryDescriptorWithDependencies(schemaContent, dependencies);
                    } else {
                        // Text format - use protobuf4j to parse and compile
                        ProtobufSchemaUtils.parseAndCompile("schema.proto", schemaContent, dependencies);
                    }
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
            String schemaContent = content.getContent().content();
            Set<String> mappedRefs = references.stream().map(ref -> ref.getName())
                    .collect(Collectors.toSet());

            Set<String> allImports;

            // Check if this is a base64-encoded binary descriptor
            if (ProtobufSchemaUtils.isBase64BinaryDescriptor(schemaContent)) {
                // For binary descriptors, extract dependencies from the FileDescriptorProto
                allImports = extractDependenciesFromBinaryDescriptor(schemaContent);
            } else {
                // Extract imports from proto text WITHOUT compiling
                // This avoids "File not found" errors when dependencies don't exist
                // We only care about non-well-known imports for reference validation
                allImports = ProtobufSchemaUtils.extractNonWellKnownImports(schemaContent);
            }

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

    /**
     * Extract dependencies from a base64-encoded binary descriptor.
     * Filters out well-known types.
     */
    private Set<String> extractDependenciesFromBinaryDescriptor(String base64Content) {
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(base64Content.trim());
            com.google.protobuf.DescriptorProtos.FileDescriptorProto proto =
                    com.google.protobuf.DescriptorProtos.FileDescriptorProto.parseFrom(decoded);

            // Extract dependencies, filtering out well-known types
            return proto.getDependencyList().stream()
                    .filter(dep -> !isWellKnownType(dep))
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            return Set.of();
        }
    }

    /**
     * Check if a dependency is a well-known type that doesn't need to be mapped.
     */
    private boolean isWellKnownType(String dependency) {
        return dependency.startsWith("google/protobuf/")
                || dependency.startsWith("google/type/");
    }
}
