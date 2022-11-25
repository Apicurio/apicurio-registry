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

package io.apicurio.registry.util;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.MediaType;

import com.google.protobuf.DescriptorProtos;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;
import org.apache.avro.Schema;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;
import io.apicurio.registry.storage.InvalidArtifactTypeException;

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
     * Figures out the artifact type in the following order of precedent:
     * <p>
     * 1) The provided X-Registry-String header
     * 2) A hint provided in the Content-Type header
     * 3) Determined from the content itself
     *
     * @param content       the content
     * @param xString the artifact type
     * @param contentType   content type from request API
     */
    //FIXME:references artifact must be dereferenced here otherwise this will fail to discover the type
    public static String determineArtifactType(ContentHandle content, String xArtifactType, String contentType, List<String> availableTypes) {
       return determineArtifactType(content, xArtifactType, contentType, Collections.emptyMap(), availableTypes);
    }

    public static String determineArtifactType(ContentHandle content, String xArtifactType, String contentType, Map<String, ContentHandle> resolvedReferences, List<String> availableTypes) {
        String artifactType = xArtifactType;
        if (artifactType == null) {
            artifactType = getArtifactTypeFromContentType(contentType, availableTypes);
            if (artifactType == null) {
                artifactType = ArtifactTypeUtil.discoverType(content, contentType, resolvedReferences);
            }
        }
        return artifactType;    }

    /**
     * Tries to figure out the artifact type by analyzing the content-type.
     *
     * @param contentType the content type header
     */
    private static String getArtifactTypeFromContentType(String contentType, List<String> availableTypes) {
        if (contentType != null && contentType.contains(MediaType.APPLICATION_JSON) && contentType.indexOf(';') != -1) {
            String[] split = contentType.split(";");
            if (split.length > 1) {
                for (String s : split) {
                    if (s.contains("artifactType=")) {
                        String at = s.split("=")[1];
                        for (String t: availableTypes) {
                            if (t.equals(at)) {
                                return at;
                            }
                        }
                        throw new BadRequestException("Unsupported artifact type: " + at);
                    }
                }
            }
        }
        if (contentType != null && contentType.contains("x-proto")) {
            return ArtifactType.PROTOBUF;
        }
        if (contentType != null && contentType.contains("graphql")) {
            return ArtifactType.GRAPHQL;
        }
        return null;
    }

    // TODO: should we move this to ArtifactTypeUtilProvider and make this logic injectable?
    // as a first implementation forcing users to specify the type if it's custom sounds like a reasonable tradeoff
    /**
     * Method that discovers the artifact type from the raw content of an artifact. This will attempt to parse
     * the content (with the optional provided Content Type as a hint) and figure out what type of artifact it
     * is. Examples include Avro, Protobuf, OpenAPI, etc. Most of the supported artifact types are JSON
     * formatted. So in these cases we will need to look for some sort of type-specific marker in the content
     * of the artifact. The method does its best to figure out the type, but will default to Avro if all else
     * fails.
     *  @param content
     * @param contentType
     * @param resolvedReferences
     */
    @SuppressWarnings("deprecation")
    private static String discoverType(ContentHandle content, String contentType, Map<String, ContentHandle> resolvedReferences) throws InvalidArtifactTypeException {
        boolean triedProto = false;

        // If the content-type suggests it's protobuf, try that first.
        if (contentType == null || contentType.toLowerCase().contains("proto")) {
            triedProto = true;
            String type = tryProto(content);
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
            if (tree.has("$schema") && tree.get("$schema").asText().contains("json-schema.org") || tree.has("properties")) {
                return ArtifactType.JSON;
            }
            // Kafka Connect??
            // TODO detect Kafka Connect schemas

            throw new InvalidArtifactTypeException("Failed to discover artifact type from JSON content.");
        } catch (Exception e) {
            // Apparently it's not JSON.
        }

        try {
            // Avro
            final Schema.Parser parser = new Schema.Parser();
            final List<Schema> schemaRefs = new ArrayList<>();
            for (Map.Entry<String, ContentHandle> referencedContent : resolvedReferences.entrySet()) {
                if (!parser.getTypes().containsKey(referencedContent.getKey())) {
                    Schema schemaRef = parser.parse(referencedContent.getValue().content());
                    schemaRefs.add(schemaRef);
                }
            }
            final Schema schema = parser.parse(content.content());
            schema.toString(schemaRefs, false);
            return ArtifactType.AVRO;
        } catch (Exception e) {
            //ignored
        }

        // Try protobuf (only if we haven't already)
        if (!triedProto) {
            String type = tryProto(content);
            if (type != null) {
                return type;
            }
        }

        // Try GraphQL (SDL)
        if (tryGraphQL(content)) {
            return ArtifactType.GRAPHQL;
        }

        // Try the various XML formatted types
        try (InputStream stream = content.stream()) {
            Document xmlDocument = DocumentBuilderAccessor.getDocumentBuilder().parse(stream);
            Element root = xmlDocument.getDocumentElement();
            String ns = root.getNamespaceURI();

            // XSD
            if (ns != null && ns.equals("http://www.w3.org/2001/XMLSchema")) {
                return ArtifactType.XSD;
            } // WSDL
            else if (ns != null && (ns.equals("http://schemas.xmlsoap.org/wsdl/")
                    || ns.equals("http://www.w3.org/ns/wsdl/"))) {
                return ArtifactType.WSDL;
            } else {
                // default to XML since its been parsed
                return ArtifactType.XML;
            }
        } catch (Exception e) {
            // It's not XML.
        }

        throw new InvalidArtifactTypeException("Failed to discover artifact type from content.");
    }

    private static String tryProto(ContentHandle content) {
        try {
            ProtobufFile.toProtoFileElement(content.content());
            return ArtifactType.PROTOBUF;
        } catch (Exception e) {
            try {
                // Attempt to parse binary FileDescriptorProto
                byte[] bytes = Base64.getDecoder().decode(content.content());
                FileDescriptorUtils.fileDescriptorToProtoFile(DescriptorProtos.FileDescriptorProto.parseFrom(bytes));
                return ArtifactType.PROTOBUF;
            } catch (Exception pe) {
                // Doesn't seem to be protobuf
            }
        }
        return null;
    }

    private static boolean tryGraphQL(ContentHandle content) {
        try {
            TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(content.content());
            if (typeRegistry != null) {
                return true;
            }
        } catch (Exception e) {
            // Must not be a GraphQL file
        }
        return false;
    }
}
