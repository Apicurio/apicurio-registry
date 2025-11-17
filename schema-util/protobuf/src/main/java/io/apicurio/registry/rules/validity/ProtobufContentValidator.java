package io.apicurio.registry.rules.validity;

import com.google.protobuf.Descriptors;
import com.squareup.wire.schema.internal.parser.MessageElement;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.RuleViolation;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchema;

import java.util.HashSet;
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
     * Validates a Protobuf schema for syntax and/or semantic correctness.
     *
     * Validation levels:
     * - SYNTAX_ONLY: Validates only that the content can be parsed as a Protobuf schema.
     *   Uses Wire library parser to check basic syntax.
     * - FULL: Validates both syntax and semantic correctness using Google Protobuf library.
     *   This catches semantic errors including:
     *   - Duplicate field tag numbers
     *   - Negative or zero field tag numbers
     *   - Invalid field types
     *   - Invalid option values
     *   - Other semantic violations
     *
     * @see io.apicurio.registry.rules.validity.ContentValidator#validate(ValidityLevel, TypedContent, Map)
     */
    @Override
    public void validate(ValidityLevel level, TypedContent content,
                         Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
        if (level == ValidityLevel.SYNTAX_ONLY) {
            // For SYNTAX_ONLY, just parse with Wire library to verify basic syntax
            try {
                ProtobufFile.toProtoFileElement(content.getContent().content());
            } catch (Exception e) {
                throw new RuleViolationException("Syntax violation for Protobuf artifact.", RuleType.VALIDITY,
                        level.name(), e);
            }
        } else if (level == ValidityLevel.FULL) {
            // For FULL validation, build FileDescriptor to perform comprehensive semantic validation
            try {
                if (resolvedReferences == null || resolvedReferences.isEmpty()) {
                    // Parse the schema to ProtoFileElement using Wire library
                    ProtoFileElement protoFileElement = ProtobufFile.toProtoFileElement(content.getContent().content());

                    // Build FileDescriptor using Google Protobuf library for comprehensive semantic validation
                    // This will catch duplicate tags, negative tags, invalid types, invalid options, etc.
                    getFileDescriptorFromElement(protoFileElement);
                }
                else {

                    final Map<String, String> requiredDeps = resolvedReferences.entrySet().stream().collect(
                            Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getContent().content()));

                    final ProtoFileElement protoFileElement = ProtobufFile
                            .toProtoFileElement(content.getContent().content());

                    final Set<FileDescriptorUtils.ProtobufSchemaContent> dependencies = resolvedReferences.entrySet()
                            .stream()
                            .map(e -> FileDescriptorUtils.ProtobufSchemaContent.of(e.getKey(), e.getValue().getContent().content()))
                            .collect(Collectors.toSet());

                    MessageElement firstMessage = FileDescriptorUtils.firstMessage(protoFileElement);

                    FileDescriptorUtils.ProtobufSchemaContent mainFile = FileDescriptorUtils.ProtobufSchemaContent.of(firstMessage.getName(),
                            content.getContent().content());

                    // This performs comprehensive validation via FileDescriptor.buildFrom()
                    FileDescriptorUtils.parseProtoFileWithDependencies(mainFile, dependencies, requiredDeps, true, true);
                }
            }
            catch (Exception e) {
                throw new RuleViolationException("Syntax or semantic violation for Protobuf artifact.", RuleType.VALIDITY,
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

            ProtoFileElement protoFileElement = ProtobufFile
                    .toProtoFileElement(content.getContent().content());
            Set<String> allImports = new HashSet<>();
            allImports.addAll(protoFileElement.getImports());
            allImports.addAll(protoFileElement.getPublicImports());

            Set<RuleViolation> violations = allImports.stream()
                    .filter(_import -> !mappedRefs.contains(_import)).map(missingRef -> new RuleViolation("Unmapped reference detected.", missingRef))
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

    private ProtobufSchema getFileDescriptorFromElement(ProtoFileElement fileElem)
            throws Descriptors.DescriptorValidationException {
        Descriptors.FileDescriptor fileDescriptor = FileDescriptorUtils.protoFileToFileDescriptor(fileElem);
        return new ProtobufSchema(fileDescriptor, fileElem);
    }
}
