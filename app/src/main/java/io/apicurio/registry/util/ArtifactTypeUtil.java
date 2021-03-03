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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.apicurio.registry.common.proto.Serde;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.compatibility.ProtobufFile;
import io.apicurio.registry.storage.InvalidArtifactTypeException;
import io.apicurio.registry.types.ArtifactType;
import uk.gov.nationalarchives.csv.validator.api.java.CsvValidator;
import uk.gov.nationalarchives.csv.validator.api.java.FailMessage;
import uk.gov.nationalarchives.csv.validator.api.java.Substitution;

/**
 * @author eric.wittmann@gmail.com
 */
public final class ArtifactTypeUtil {

    private static final ObjectMapper mapper = new ObjectMapper();
    protected final static Logger log = LoggerFactory.getLogger(ArtifactTypeUtil.class);
    /**
     * Constructor.
     */
    private ArtifactTypeUtil() {
    }

    /**
     * Method that discovers the artifact type from the raw content of an artifact. This will attempt to parse
     * the content (with the optional provided Content Type as a hint) and figure out what type of artifact it
     * is. Examples include Avro, Protobuf, OpenAPI, etc. Most of the supported artifact types are JSON
     * formatted. So in these cases we will need to look for some sort of type-specific marker in the content
     * of the artifact. The method does its best to figure out the type, but will default to Avro if all else
     * fails.
     * 
     * @param content
     * @param contentType
     */
    public static ArtifactType discoverType(ContentHandle content, String contentType) throws InvalidArtifactTypeException {
        boolean triedProto = false;

        // If the content-type suggests it's protobuf, try that first.
        if (contentType == null || contentType.toLowerCase().contains("proto")) {
            triedProto = true;
            ArtifactType type = tryProto(content);
            if (type != null) {
                return type;
            }
        }
        
        //Try CSV
        if(tryCSV(content)) {
        	return ArtifactType.CSV;
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
            // Kafka Connect??
            // TODO detect Kafka Connect schemas
            // Avro
            if (tree.has("type")) {
                return ArtifactType.AVRO;
            }
            
            throw new InvalidArtifactTypeException("Failed to discover artifact type from JSON content.");
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

    private static ArtifactType tryProto(ContentHandle content) {
        try {
            ProtobufFile.toProtoFileElement(content.content());
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

    private static boolean tryCSV(ContentHandle content) {
    	
    
        List<FailMessage> failures = CsvValidator.validate(new InputStreamReader(new ByteArrayInputStream("test,test,test".getBytes())), new InputStreamReader(content.stream()), false,  new ArrayList<Substitution>(), Boolean.TRUE, Boolean.FALSE);
        if(failures != null && !failures.isEmpty()) {
        	for(FailMessage msg : failures) {
        		
        		if(failures.size() == 1 && msg.getMessage().startsWith("Metadata header, cannot find")) {
        			return true;
        		}
        		
        		log.info("CSV Parse error message: " + msg.getMessage());
        	}
        	
        	return false;
        }
        return false;
    }
}
