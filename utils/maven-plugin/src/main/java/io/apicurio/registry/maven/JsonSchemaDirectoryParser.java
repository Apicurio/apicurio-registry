package io.apicurio.registry.maven;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rules.SomeJsonSchema;
import io.apicurio.registry.rules.compatibility.jsonschema.JsonUtil;
import io.apicurio.registry.types.ContentTypes;
import org.everit.json.schema.ArraySchema;
import org.everit.json.schema.ObjectSchema;
import org.everit.json.schema.ReferenceSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class JsonSchemaDirectoryParser extends AbstractDirectoryParser<SomeJsonSchema> {

    private static final String JSON_SCHEMA_EXTENSION = ".json";
    private static final Logger log = LoggerFactory.getLogger(JsonSchemaDirectoryParser.class);

    public JsonSchemaDirectoryParser(RegistryClient client) {
        super(client);
    }

    @Override
    public ParsedDirectoryWrapper<SomeJsonSchema> parse(File rootSchemaFile) {
        return parseDirectory(rootSchemaFile.getParentFile(), rootSchemaFile);
    }

    @Override
    public List<ArtifactReference> handleSchemaReferences(RegisterArtifact rootArtifact,
                                                          SomeJsonSchema someRootSchema, Map<String, TypedContent> fileContents)
            throws FileNotFoundException, ExecutionException, InterruptedException {

        if (someRootSchema.getJsonsKema() != null) {
            log.warn("Reference handling for JSON schema version 2020-12 is not supported yet.");
            return List.of();
        }
        var rootSchema = someRootSchema.getEverit();

        if (rootSchema instanceof ObjectSchema) {

            ObjectSchema objectSchema = (ObjectSchema) rootSchema;
            Set<ArtifactReference> references = new HashSet<>();

            Map<String, org.everit.json.schema.Schema> rootSchemaPropertySchemas = objectSchema
                    .getPropertySchemas();

            for (String schemaKey : rootSchemaPropertySchemas.keySet()) {

                List<ArtifactReference> nestedArtifactReferences = new ArrayList<>();

                if (rootSchemaPropertySchemas.get(schemaKey) instanceof ReferenceSchema) {

                    ReferenceSchema nestedSchema = (ReferenceSchema) rootSchemaPropertySchemas.get(schemaKey);
                    RegisterArtifact nestedRegisterArtifact = buildFromRoot(rootArtifact,
                            nestedSchema.getSchemaLocation());

                    if (nestedSchema.getReferredSchema() instanceof ObjectSchema) {
                        ObjectSchema nestedObjectSchema = (ObjectSchema) nestedSchema.getReferredSchema();
                        nestedArtifactReferences = handleSchemaReferences(nestedRegisterArtifact,
                                new SomeJsonSchema(nestedObjectSchema), fileContents);
                    }

                    references.add(registerNestedSchema(nestedSchema.getSchemaLocation(),
                            nestedArtifactReferences, nestedRegisterArtifact,
                            fileContents.get(nestedSchema.getSchemaLocation()).getContent().content()));

                } else if (rootSchemaPropertySchemas.get(schemaKey) instanceof ArraySchema) {

                    final ArraySchema arraySchema = (ArraySchema) rootSchemaPropertySchemas.get(schemaKey);
                    if (arraySchema.getAllItemSchema() instanceof ReferenceSchema) {

                        ReferenceSchema arrayElementSchema = (ReferenceSchema) arraySchema.getAllItemSchema();
                        RegisterArtifact nestedRegisterArtifact = buildFromRoot(rootArtifact,
                                arrayElementSchema.getSchemaLocation());

                        if (arrayElementSchema.getReferredSchema() instanceof ObjectSchema) {

                            nestedArtifactReferences = handleSchemaReferences(nestedRegisterArtifact,
                                    new SomeJsonSchema(arrayElementSchema), fileContents);
                        }
                        references.add(registerNestedSchema(arrayElementSchema.getSchemaLocation(),
                                nestedArtifactReferences, nestedRegisterArtifact, fileContents
                                        .get(arrayElementSchema.getSchemaLocation()).getContent().content()));
                    }
                }
            }
            return new ArrayList<>(references);
        } else {
            return Collections.emptyList();
        }
    }

    private JsonSchemaWrapper parseDirectory(File directory, File rootSchema) {
        Set<File> typesToAdd = Arrays
                .stream(Objects.requireNonNull(
                        directory.listFiles((dir, name) -> name.endsWith(JSON_SCHEMA_EXTENSION))))
                .filter(file -> !file.getName().equals(rootSchema.getName())).collect(Collectors.toSet());

        Map<String, SomeJsonSchema> processed = new HashMap<>();
        Map<String, TypedContent> schemaContents = new HashMap<>();

        while (processed.size() != typesToAdd.size()) {
            boolean fileParsed = false;
            for (File typeToAdd : typesToAdd) {
                if (typeToAdd.getName().equals(rootSchema.getName())) {
                    continue;
                }
                try {
                    final ContentHandle schemaContent = readSchemaContent(typeToAdd);
                    final TypedContent typedSchemaContent = TypedContent.create(schemaContent,
                            ContentTypes.APPLICATION_JSON);
                    final SomeJsonSchema schema = JsonUtil.readSchema(schemaContent.content(), schemaContents, false);
                    processed.put(schema.getId(), schema);
                    schemaContents.put(schema.getId(), typedSchemaContent);
                    fileParsed = true;
                } catch (JsonProcessingException ex) {
                    log.warn(
                            "Error processing json schema with name {}. This usually means that the references are not ready yet to parse it",
                            typeToAdd.getName());
                }
            }

            // If no schema has been processed during this iteration, that means there is an error in the
            // configuration, throw exception.
            if (!fileParsed) {
                throw new IllegalStateException(
                        "Error found in the directory structure. Check that all required files are present.");
            }
        }

        try {
            return new JsonSchemaWrapper(
                    JsonUtil.readSchema(readSchemaContent(rootSchema).content(), schemaContents, false),
                    schemaContents);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Unable to parse main schema", e);
        }
    }

    public static class JsonSchemaWrapper implements ParsedDirectoryWrapper<SomeJsonSchema> {
        final SomeJsonSchema schema;
        final Map<String, TypedContent> fileContents;

        public JsonSchemaWrapper(SomeJsonSchema schema, Map<String, TypedContent> fileContents) {
            this.schema = schema;
            this.fileContents = fileContents;
        }

        @Override
        public SomeJsonSchema getSchema() {
            return schema;
        }

        @Override
        public Map<String, TypedContent> getSchemaContents() {
            return fileContents;
        }
    }

}
