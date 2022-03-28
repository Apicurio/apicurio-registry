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
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author carnalca@redhat.com
 */
public class ProtobufDereferencer implements ContentDereferencer {

    @Override
    public ContentHandle dereference(ContentHandle content, Map<String, ContentHandle> resolvedReferences) {
        //FIXME this code is not dereferencing references, only validating that all that references are resolvable
        final ProtoFileElement protoFileElement = ProtobufFile.toProtoFileElement(content.content());
        final Map<String, ProtoFileElement> dependencies = Collections.unmodifiableMap(resolvedReferences.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> ProtobufFile.toProtoFileElement(e.getValue().content())
                )));
        try {
            return ContentHandle.create(FileDescriptorUtils.fileDescriptorWithDepsToProtoFile(FileDescriptorUtils.protoFileToFileDescriptor(protoFileElement), dependencies).toString());
        } catch (Descriptors.DescriptorValidationException e) {
            throw new IllegalArgumentException(e);
        }
    }
}