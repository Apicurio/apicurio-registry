package io.apicurio.registry.rules.validity;

import com.google.protobuf.Descriptors;
import com.squareup.wire.schema.internal.parser.MessageElement;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.RuleViolation;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchema;

import java.util.Collections;
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
     * @see io.apicurio.registry.rules.validity.ContentValidator#validate(ValidityLevel, ContentHandle, Map)
     */
    @Override
    public void validate(ValidityLevel level, ContentHandle artifactContent,
            Map<String, ContentHandle> resolvedReferences) throws RuleViolationException {
        if (level == ValidityLevel.SYNTAX_ONLY || level == ValidityLevel.FULL) {
            try {
                if (resolvedReferences == null || resolvedReferences.isEmpty()) {
                    ProtobufFile.toProtoFileElement(artifactContent.content());
                } else {
                    final ProtoFileElement protoFileElement = ProtobufFile
                            .toProtoFileElement(artifactContent.content());
                    final Map<String, ProtoFileElement> dependencies = Collections.unmodifiableMap(
                            resolvedReferences.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                                    e -> ProtobufFile.toProtoFileElement(e.getValue().content()))));
                    MessageElement firstMessage = FileDescriptorUtils.firstMessage(protoFileElement);
                    if (firstMessage != null) {
                        try {
                            final Descriptors.Descriptor fileDescriptor = FileDescriptorUtils
                                    .toDescriptor(firstMessage.getName(), protoFileElement, dependencies);
                            ContentHandle.create(fileDescriptor.toString());
                        } catch (IllegalStateException ise) {
                            // If we fail to init the dynamic schema, try to get the descriptor from the proto
                            // element
                            ContentHandle.create(getFileDescriptorFromElement(protoFileElement).toString());
                        }
                    } else {
                        ContentHandle.create(getFileDescriptorFromElement(protoFileElement).toString());
                    }
                }
            } catch (Exception e) {
                throw new RuleViolationException("Syntax violation for Protobuf artifact.", RuleType.VALIDITY,
                        level.name(), e);
            }
        }
    }

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validateReferences(io.apicurio.registry.content.ContentHandle,
     *      java.util.List)
     */
    @Override
    public void validateReferences(ContentHandle artifactContent, List<ArtifactReference> references)
            throws RuleViolationException {
        try {
            Set<String> mappedRefs = references.stream().map(ref -> ref.getName())
                    .collect(Collectors.toSet());

            ProtoFileElement protoFileElement = ProtobufFile.toProtoFileElement(artifactContent.content());
            Set<String> allImports = new HashSet<>();
            allImports.addAll(protoFileElement.getImports());
            allImports.addAll(protoFileElement.getPublicImports());

            Set<RuleViolation> violations = allImports.stream()
                    .filter(_import -> !mappedRefs.contains(_import)).map(missingRef -> {
                        return new RuleViolation("Unmapped reference detected.", missingRef);
                    }).collect(Collectors.toSet());
            if (!violations.isEmpty()) {
                throw new RuleViolationException("Unmapped reference(s) detected.", RuleType.INTEGRITY,
                        IntegrityLevel.ALL_REFS_MAPPED.name(), violations);
            }
        } catch (RuleViolationException rve) {
            throw rve;
        } catch (Exception e) {
            // Do nothing - we don't care if it can't validate. Another rule will handle that.
        }
    }

    private ProtobufSchema getFileDescriptorFromElement(ProtoFileElement fileElem)
            throws Descriptors.DescriptorValidationException {
        Descriptors.FileDescriptor fileDescriptor = FileDescriptorUtils.protoFileToFileDescriptor(fileElem);
        return new ProtobufSchema(fileDescriptor, fileElem);
    }
}
