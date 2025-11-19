package io.apicurio.registry.maven.refs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.models.Document;
import io.apicurio.datamodels.util.ModelTypeUtil;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * An index of the files available when discovering references in an artifact. This index is typically
 * populated by getting a list of all files in a directory, or zip file. The index maps a resource name (this
 * will vary depending on the artifact type) to the content of the resource. For example, Avro schemas will
 * have resource names based on the qualified name of the type they define. JSON Schemas will have resources
 * names based on the name of the file. The intent of this index is to resolve an external reference found in
 * an artifact to an actual piece of content (e.g. file) in the index. If it cannot be resolved, that would
 * typically mean that there is a broken reference in the schema/design.
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
     * 
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
     * Look up a resource in the index. Returns <code>null</code> if no resource with that name is found.
     * 
     * @param resourceName
     * @param relativeToFile
     */
    public IndexedResource lookup(String resourceName, Path relativeToFile) {
        return index.stream().filter(resource -> resource.matches(resourceName, relativeToFile, schemaPaths))
                .findFirst().orElse(null);
    }

    /**
     * Index an existing (remote) reference using a resource name and remote artifact metadata.
     * 
     * @param resourceName
     * @param vmd
     */
    public void index(String resourceName, VersionMetaData vmd) {
        IndexedResource res = new IndexedResource(null, null, resourceName, null);
        res.setRegistration(vmd);
        this.index.add(res);
    }

    /**
     * Index the given content. Indexing will parse the content and figure out its resource name and type.
     *
     * @param path
     * @param content
     */
    public void index(Path path, ContentHandle content) {
        try {
            // Determine content type based on file extension
            String contentType = ContentTypes.APPLICATION_JSON;
            String fileName = path.getFileName().toString().toLowerCase();
            if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
                contentType = ContentTypes.APPLICATION_YAML;
            }

            // Parse JSON or YAML content
            TypedContent typedContent = TypedContent.create(content, contentType);
            JsonNode tree = ContentTypeUtil.parseJsonOrYaml(typedContent);

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
            // Must not be JSON or YAML...
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
        // Validate that the content is a valid protobuf schema
        try {
            new ProtobufFile(content.content());
        } catch (Exception e) {
            throw new RuntimeException("Invalid protobuf schema: " + path, e);
        }

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
        try {
            // Determine content type based on file extension
            String contentType = ContentTypes.APPLICATION_JSON;
            String fileName = path.getFileName().toString().toLowerCase();
            if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
                contentType = ContentTypes.APPLICATION_YAML;
            }

            // Parse JSON or YAML content
            TypedContent typedContent = TypedContent.create(content, contentType);
            JsonNode node = ContentTypeUtil.parseJsonOrYaml(typedContent);
            Document doc = Library.readDocument((ObjectNode) node);

            if (doc == null) {
                throw new UnsupportedOperationException("Content is not OpenAPI or AsyncAPI.");
            }

            String type = ArtifactType.OPENAPI;
            if (ModelTypeUtil.isAsyncApiModel(doc)) {
                type = ArtifactType.ASYNCAPI;
            }

            IndexedResource resource = new IndexedResource(path, type, null, content);
            this.index.add(resource);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse OpenAPI/AsyncAPI document", e);
        }
    }

}
