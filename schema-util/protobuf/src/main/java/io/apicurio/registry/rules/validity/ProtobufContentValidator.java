/*
 * Copyright 2020 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.rules.validity;

import com.google.protobuf.Descriptors;
import com.squareup.wire.schema.internal.parser.MessageElement;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchema;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A content validator implementation for the Protobuf content type.
 *
 * @author eric.wittmann@gmail.com
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
    public void validate(ValidityLevel level, ContentHandle artifactContent, Map<String, ContentHandle> resolvedReferences) throws RuleViolationException {
        if (level == ValidityLevel.SYNTAX_ONLY || level == ValidityLevel.FULL) {
            try {
                if (resolvedReferences == null || resolvedReferences.isEmpty()) {
                    ProtobufFile.toProtoFileElement(artifactContent.content());
                } else {
                    final ProtoFileElement protoFileElement = ProtobufFile.toProtoFileElement(artifactContent.content());
                    final Map<String, ProtoFileElement> dependencies = Collections.unmodifiableMap(resolvedReferences.entrySet()
                            .stream()
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    e -> ProtobufFile.toProtoFileElement(e.getValue().content())
                            )));
                    MessageElement firstMessage = FileDescriptorUtils.firstMessage(protoFileElement);
                    if (firstMessage != null) {
                        try {
                            final Descriptors.Descriptor fileDescriptor = FileDescriptorUtils.toDescriptor(firstMessage.getName(), protoFileElement, dependencies);
                            ContentHandle.create(fileDescriptor.toString());
                        } catch (IllegalStateException ise) {
                            //If we fail to init the dynamic schema, try to get the descriptor from the proto element
                            ContentHandle.create(getFileDescriptorFromElement(protoFileElement).toString());
                        }
                    } else {
                        ContentHandle.create(getFileDescriptorFromElement(protoFileElement).toString());
                    }
                }
            } catch (Exception e) {
                throw new RuleViolationException("Syntax violation for Protobuf artifact.", RuleType.VALIDITY, level.name(), e);
            }
        }
    }

    private ProtobufSchema getFileDescriptorFromElement(ProtoFileElement fileElem) throws Descriptors.DescriptorValidationException {
        Descriptors.FileDescriptor fileDescriptor = FileDescriptorUtils.protoFileToFileDescriptor(fileElem);
        return new ProtobufSchema(fileDescriptor, fileElem);
    }
}
