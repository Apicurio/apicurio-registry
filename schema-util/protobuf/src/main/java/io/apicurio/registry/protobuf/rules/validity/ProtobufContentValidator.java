package io.apicurio.registry.protobuf.rules.validity;

import com.squareup.wire.schema.SchemaException;
import com.squareup.wire.schema.internal.parser.MessageElement;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.validity.AbstractContentValidator;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A content validator implementation for the Protobuf content type.
 */
public class ProtobufContentValidator extends AbstractContentValidator {

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
            // Run FQN / identifier hardening first. The detector contract guarantees
            // RuleViolationException is the only type that can escape this call — it
            // catches and wraps any parser / base64 / descriptor failures from its own
            // inputs internally — so no outer try/catch is needed here.
            Map<String, String> referenceTextSchemas = ProtobufFqnConflictDetector
                    .assertNoConflicts(level, content, resolvedReferences);
            try {
                if (resolvedReferences == null || resolvedReferences.isEmpty()) {
                    // Parse the protobuf content (syntax validation)
                    ProtoFileElement protoFileElement = ProtobufFile
                            .toProtoFileElement(content.getContent().content());
                    // Attempt semantic validation by building a FileDescriptor
                    // This validates: duplicate tags, invalid tag numbers, unknown types, etc.
                    try {
                        FileDescriptorUtils.protoFileToFileDescriptor(protoFileElement);
                    } catch (RuntimeException e) {
                        // Check if this is a semantic error or a resource loading issue
                        Throwable cause = e.getCause();
                        if (cause instanceof SchemaException) {
                            // Semantic error from Wire's schema linker - re-throw to fail validation
                            throw e;
                        }
                        if (cause instanceof IOException || cause instanceof NullPointerException) {
                            // Resource loading failure (e.g., in native mode where proto resources
                            // may not be available) - fall back to syntax-only validation.
                            // Syntax validation already passed above, so continue.
                            return;
                        }
                        // For other RuntimeExceptions, check the message for semantic error indicators
                        String message = e.getMessage() != null ? e.getMessage() : "";
                        if (message.contains("SchemaException") || message.contains("multiple fields share tag")
                                || message.contains("unable to resolve")) {
                            // This looks like a semantic error - re-throw
                            throw e;
                        }
                        // Unknown error type - fall back to syntax-only to avoid breaking native mode
                    }
                }
                else {
                    // Convert main content if binary (base64-encoded)
                    final ProtoFileElement protoFileElement = ProtobufFile
                            .toProtoFileElement(content.getContent().content());
                    String textMainContent = protoFileElement.toSchema();

                    // Reuse the text schemas already materialized by the FQN detector to
                    // avoid converting every reference twice (once there, once here).
                    final Map<String, String> requiredDeps = referenceTextSchemas;

                    final Set<FileDescriptorUtils.ProtobufSchemaContent> dependencies = requiredDeps.entrySet()
                            .stream()
                            .map(e -> FileDescriptorUtils.ProtobufSchemaContent.of(e.getKey(), e.getValue()))
                            .collect(Collectors.toSet());

                    MessageElement firstMessage = FileDescriptorUtils.firstMessage(protoFileElement);
                    String fileName = firstMessage != null ? firstMessage.getName() : "schema";

                    FileDescriptorUtils.ProtobufSchemaContent mainFile = FileDescriptorUtils.ProtobufSchemaContent.of(fileName,
                            textMainContent);

                    FileDescriptorUtils.parseProtoFileWithDependencies(mainFile, dependencies, requiredDeps, true, true);
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
            ProtoFileElement protoFileElement = ProtobufFile
                    .toProtoFileElement(content.getContent().content());
            Set<String> allImports = new HashSet<>();
            allImports.addAll(protoFileElement.getImports());
            allImports.addAll(protoFileElement.getPublicImports());

            validateMappedReferences(references, allImports, "Unmapped reference detected.");
        }
        catch (RuleViolationException rve) {
            throw rve;
        }
        catch (Exception e) {
            // Do nothing - we don't care if it can't validate. Another rule will handle that.
        }
    }
}
