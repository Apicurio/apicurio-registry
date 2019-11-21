/*
 * Copyright 2019 Red Hat
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

package io.apicurio.registry.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.internal.parser.ProtoParser;
import io.apicurio.registry.common.proto.Serde;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.content.ContentHandle;

/**
 * @author eric.wittmann@gmail.com
 */
public final class ArtifactTypeUtil {
    
    private static final ObjectMapper mapper = new ObjectMapper();
    
    /**
     * Constructor.
     */
    private ArtifactTypeUtil() {
    }
    
    /**
     * Method that discovers the artifact type from the raw content of an artifact.  This will
     * attempt to parse the content (with the optional provided Content Type as a hint) and figure
     * out what type of artifact it is.  Examples include Avro, Protobuf, OpenAPI, etc.  Most
     * of the supported artifact types are JSON formatted.  So in these cases we will need to 
     * look for some sort of type-specific marker in the content of the artifact.  The method 
     * does its best to figure out the type, but will default to Avro if all else fails.
     * 
     * @param content
     * @param contentType
     */
    public static ArtifactType discoverType(ContentHandle content, String contentType) {
        boolean triedProto = false;
        
        // If the content-type suggest it's protobuf, try that first.
        if (contentType == null || contentType.toLowerCase().contains("proto")) {
            triedProto = true;
            ArtifactType type = tryProto(content);
            if (type != null) {
                return type;
            }
        }
        
        // Try the various JSON formatted types
        try {
            JsonNode tree = mapper.readTree(content.content());
            
            // OpenAPI
            if (tree.has("openapi") || tree.has("swagger")) {
                return ArtifactType.OPENAPI;
            }
            // AsyncAPI
            if (tree.has("asyncapi")) {
                return ArtifactType.ASYNCAPI;
            }
            // JSON Schema
            if (tree.has("$schema") && tree.get("$schema").asText().contains("json-schema.org")) {
                return ArtifactType.JSON;
            }
            // Avro
            return ArtifactType.AVRO;
        } catch (Exception e) {
            // Apparently it's not JSON.
        }
        
        // Try protobuf (only if we haven't already)
        if (!triedProto) {
            ArtifactType type = tryProto(content);
            if (type != null) {
                return type;
            }
        }
        
        // Default to Avro
        return ArtifactType.AVRO;
    }

    private static ArtifactType tryProto(ContentHandle content) {
        try {
            ProtoParser.parse(Location.get(""), content.content());
            return ArtifactType.PROTOBUF;
        } catch (Exception e) {
            // Doesn't seem to be protobuf
        }
        try {
            Serde.Schema.parseFrom(content.bytes());
            return ArtifactType.PROTOBUF_FD;
        } catch (Exception e) {
            // Doesn't seem to be protobuf_fd
        }
        return null;
    }

}
