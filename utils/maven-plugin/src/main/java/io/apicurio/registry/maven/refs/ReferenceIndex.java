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

package io.apicurio.registry.maven.refs;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.models.Document;
import io.apicurio.datamodels.util.ModelTypeUtil;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;

/**
 * An index of the files available when discovering references in an artifact.  This index is 
 * typically populated by getting a list of all files in a directory, or zip file.
 * 
 * The index maps a resource name (this will vary depending on the artifact type) to the 
 * content of the resource.  For example, Avro schemas will have resource names based on the
 * qualified name of the type they define.  JSON Schemas will have resources names based on
 * the name of the file.
 * 
 * The intent of this index is to resolve an external reference found in an artifact to an
 * actual piece of content (e.g. file) in the index.  If it cannot be resolved, that would
 * typically mean that there is a broken reference in the schema/design.
 * 
 * @author eric.wittmann@gmail.com
 */
public class ReferenceIndex {

    private static final ObjectMapper mapper = new ObjectMapper();

    private Set<IndexedResource> index = new HashSet<>();
    private Set<Path> schemaPaths = new HashSet<>();
    
    /**
     * Constructor.
     */
    public ReferenceIndex() {
    }
    
    /**
     * Constructor.
     * @param schemaPath
     */
    public ReferenceIndex(Path schemaPath) {
        this.schemaPaths.add(schemaPath);
    }
    
    /**
     * @param path
     */
    public void addSchemaPath(Path path) {
        this.schemaPaths.add(path);
    }

    /**
     * Look up a resource in the index.  Returns <code>null</code> if no resource with that
     * name is found.
     * @param resourceName
     * @param relativeToFile
     */
    public IndexedResource lookup(String resourceName, Path relativeToFile) {
        return index.stream().filter(resource -> resource.matches(resourceName, relativeToFile, schemaPaths)).findFirst().orElse(null);
    }

    /**
     * Index an existing (remote) reference using a resource name and remote artifact metadata.
     * @param resourceName
     * @param amd
     */
    public void index(String resourceName, ArtifactMetaData amd) {
        IndexedResource res = new IndexedResource(null, null, resourceName, null);
        res.setRegistration(amd);
        this.index.add(res);
    }

    /**
     * Index the given content.  Indexing will parse the content and figure out its resource
     * name and type.
     * @param path
     * @param content
     */
    public void index(Path path, ContentHandle content) {
        try {
            JsonNode tree = mapper.readTree(content.content());
    
            // OpenAPI
            if (tree.has("openapi") || tree.has("swagger") || tree.has("asyncapi")) {
                indexDataModels(path, content);
            }
            // JSON Schema
            if (tree.has("$schema") && !tree.get("$schema").isNull()) {
                indexJsonSchema(tree, path, content);
            }
            // Avro
            indexAvro(path, content, tree);
        } catch (Exception e) {
            // Must not be JSON...
        }
        
        try {
            indexProto(path, content);
            return;
        } catch (Exception e) {
            // I guess it's not Protobuf.
        }
    }

    private void indexAvro(Path path, ContentHandle content, JsonNode parsed) {
        // TODO: is namespace required for an Avro schema?
        String ns = parsed.get("namespace").asText();
        String name = parsed.get("name").asText();
        String resourceName = ns != null ? ns + "." + name : name;
        IndexedResource resource = new IndexedResource(path, ArtifactType.AVRO, resourceName, content);
        this.index.add(resource);
    }

    private void indexProto(Path path, ContentHandle content) {
        ProtobufFile.toProtoFileElement(content.content());
        
        IndexedResource resource = new IndexedResource(path, ArtifactType.PROTOBUF, null, content);
        this.index.add(resource);
    }

    private void indexJsonSchema(JsonNode schema, Path path, ContentHandle content) {
        String resourceName = null;
        if (schema.has("$id")) {
            resourceName = schema.get("$id").asText(null);
        }
        IndexedResource resource = new IndexedResource(path, ArtifactType.JSON, resourceName, content);
        this.index.add(resource);
    }

    private void indexDataModels(Path path, ContentHandle content) {
        Document doc = Library.readDocumentFromJSONString(content.content());
        if (doc == null) {
            throw new UnsupportedOperationException("Content is not OpenAPI or AsyncAPI.");
        }
        
        String type = ArtifactType.OPENAPI;
        if (ModelTypeUtil.isAsyncApiModel(doc)) {
            type = ArtifactType.ASYNCAPI;
        }
        
        IndexedResource resource = new IndexedResource(path, type, null, content);
        this.index.add(resource);
    }

}
