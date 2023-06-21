/*
 * Copyright 2023 Red Hat Inc
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

package io.apicurio.registry.content.refs;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.squareup.wire.schema.internal.parser.ProtoFileElement;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;

/**
 * A Google Protocol Buffer implementation of a reference finder.
 * @author eric.wittmann@gmail.com
 */
public class ProtobufReferenceFinder implements ReferenceFinder {
    
    private static final Logger log = LoggerFactory.getLogger(ProtobufReferenceFinder.class);

    /**
     * @see io.apicurio.registry.content.refs.ReferenceFinder#findExternalReferences(io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public Set<ExternalReference> findExternalReferences(ContentHandle content) {
        try {
            ProtoFileElement protoFileElement = ProtobufFile.toProtoFileElement(content.content());
            Set<String> allImports = new HashSet<>();
            allImports.addAll(protoFileElement.getImports());
            allImports.addAll(protoFileElement.getPublicImports());
            return allImports.stream().map(imprt -> new ExternalReference(imprt)).collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("Error finding external references in a Protobuf file.", e);
            return Collections.emptySet();
        }
    }

}
