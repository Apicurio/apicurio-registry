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

package io.apicurio.registry.content.canon;

import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ProtoParser;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;

import java.util.Map;

/**
 * A Protobuf implementation of a content Canonicalizer.
 *
 * @author Fabian Martinez
 */
public class ProtobufContentCanonicalizer implements ContentCanonicalizer {

    /**
     * @see io.apicurio.registry.content.canon.ContentCanonicalizer#canonicalize(io.apicurio.registry.content.ContentHandle, Map)
     */
    @Override
    public ContentHandle canonicalize(ContentHandle content, Map<String, ContentHandle> resolvedReferences) {
        try {
            ProtoFileElement fileElem = ProtoParser.Companion.parse(FileDescriptorUtils.DEFAULT_LOCATION, content.content());

            //TODO maybe use FileDescriptorUtils to convert to a FileDescriptor and then convert back to ProtoFileElement

            return ContentHandle.create(fileElem.toSchema());
        } catch (Throwable e) {
            return content;
        }
    }

}
