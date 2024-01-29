/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.content.dereference;

import com.google.protobuf.Descriptors;
import com.squareup.wire.schema.internal.parser.MessageElement;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchema;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author carnalca@redhat.com
 */
public class ProtobufDereferencer implements ContentDereferencer {

    @Override
    public ContentHandle dereference(ContentHandle content, Map<String, ContentHandle> resolvedReferences) {
        final ProtoFileElement protoFileElement = ProtobufFile.toProtoFileElement(content.content());
        try {
            final Map<String, ProtoFileElement> dependencies = Collections.unmodifiableMap(resolvedReferences.entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> ProtobufFile.toProtoFileElement(e.getValue().content())
                    )));
            MessageElement firstMessage = FileDescriptorUtils.firstMessage(protoFileElement);
            if (firstMessage != null) {
                try {
                    final Descriptors.FileDescriptor fileDescriptor = FileDescriptorUtils.toDescriptor(firstMessage.getName(), protoFileElement, dependencies).getFile();

                    //There is already a valid type descriptor here
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    fileDescriptor.toProto().writeTo(outputStream);

                    return ContentHandle.create(outputStream.toByteArray());
                } catch (IllegalStateException ise) {
                    //If we fail to init the dynamic schema, try to get the descriptor from the proto element
                    return ContentHandle.create(getFileDescriptorFromElement(protoFileElement).toString());
                }
            } else {
                return ContentHandle.create(getFileDescriptorFromElement(protoFileElement).toString());
            }

        } catch (Descriptors.DescriptorValidationException e) {
            throw new IllegalArgumentException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ProtobufSchema getFileDescriptorFromElement(ProtoFileElement fileElem) throws Descriptors.DescriptorValidationException {
        Descriptors.FileDescriptor fileDescriptor = FileDescriptorUtils.protoFileToFileDescriptor(fileElem);
        return new ProtobufSchema(fileDescriptor, fileElem);
    }

    /**
     * @see io.apicurio.registry.content.dereference.ContentDereferencer#rewriteReferences(io.apicurio.registry.content.ContentHandle, java.util.Map)
     */
    @Override
    public ContentHandle rewriteReferences(ContentHandle content, Map<String, String> resolvedReferenceUrls) {
        return content;
    }
}