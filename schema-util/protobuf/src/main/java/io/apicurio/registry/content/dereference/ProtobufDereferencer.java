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

import com.google.protobuf.DescriptorProtos;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author carnalca@redhat.com
 */
public class ProtobufDereferencer implements ContentDereferencer {

    @Override
    public ContentHandle dereference(ContentHandle content, Map<String, ContentHandle> resolvedReferences) {
        final ProtoFileElement protoFileElement = ProtobufFile.toProtoFileElement(content.content());
        final Map<String, String> schemaDefs = Collections.unmodifiableMap(resolvedReferences.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().content()
                )));

        DescriptorProtos.FileDescriptorProto fileDescriptorProto = FileDescriptorUtils.toFileDescriptorProto(content.content(), FileDescriptorUtils.firstMessage(protoFileElement).getName(), Optional.ofNullable(protoFileElement.getPackageName()), schemaDefs);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            fileDescriptorProto.writeTo(outputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //Dereference returns the whole file descriptor bytes representing the main protobuf schema with the required dependencies.
        return ContentHandle.create(outputStream.toByteArray());
    }

    /**
     * @see io.apicurio.registry.content.dereference.ContentDereferencer#rewriteReferences(io.apicurio.registry.content.ContentHandle, java.util.Map)
     */
    @Override
    public ContentHandle rewriteReferences(ContentHandle content, Map<String, String> resolvedReferenceUrls) {
        return content;
    }
}