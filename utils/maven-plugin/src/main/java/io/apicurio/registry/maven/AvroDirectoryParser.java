package io.apicurio.registry.maven;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.types.ContentTypes;
import org.apache.avro.Schema;
import org.apache.avro.SchemaParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class AvroDirectoryParser extends AbstractDirectoryParser<Schema> {

    private static final String AVRO_SCHEMA_EXTENSION = ".avsc";
    private static final Logger log = LoggerFactory.getLogger(AvroDirectoryParser.class);

    public AvroDirectoryParser(RegistryClient client) {
        super(client);
    }

    @Override
    public ParsedDirectoryWrapper<Schema> parse(File rootSchemaFile) {
        return parseDirectory(rootSchemaFile.getParentFile(), rootSchemaFile);
    }

    @Override
    public List<ArtifactReference> handleSchemaReferences(RegisterArtifact rootArtifact, Schema rootSchema,
            Map<String, TypedContent> fileContents)
            throws FileNotFoundException, ExecutionException, InterruptedException {

        Set<ArtifactReference> references = new HashSet<>();

        // Iterate through all the fields of the schema
        for (Schema.Field field : rootSchema.getFields()) {
            List<ArtifactReference> nestedArtifactReferences = new ArrayList<>();
            if (field.schema().getType() == Schema.Type.RECORD) { // If the field is a sub-schema, recursively
                                                                  // check for nested sub-schemas and register
                                                                  // all of them

                RegisterArtifact nestedSchema = buildFromRoot(rootArtifact, field.schema().getFullName());

                if (field.schema().hasFields()) {
                    nestedArtifactReferences = handleSchemaReferences(nestedSchema, field.schema(),
                            fileContents);
                }

                references.add(registerNestedSchema(field.schema().getFullName(), nestedArtifactReferences,
                        nestedSchema, fileContents.get(field.schema().getFullName()).getContent().content()));
            } else if (field.schema().getType() == Schema.Type.ENUM) { // If the nested schema is an enum,
                                                                       // just register

                RegisterArtifact nestedSchema = buildFromRoot(rootArtifact, field.schema().getFullName());
                references.add(registerNestedSchema(field.schema().getFullName(), nestedArtifactReferences,
                        nestedSchema, fileContents.get(field.schema().getFullName()).getContent().content()));
            } else if (isArrayWithSubschemaElement(field)) { // If the nested schema is an array and the
                                                             // element is a sub-schema, handle it

                Schema elementSchema = field.schema().getElementType();

                RegisterArtifact nestedSchema = buildFromRoot(rootArtifact, elementSchema.getFullName());

                if (elementSchema.hasFields()) {
                    nestedArtifactReferences = handleSchemaReferences(nestedSchema, elementSchema,
                            fileContents);
                }

                references.add(registerNestedSchema(elementSchema.getFullName(), nestedArtifactReferences,
                        nestedSchema, fileContents.get(elementSchema.getFullName()).getContent().content()));
            }
        }
        return new ArrayList<>(references);
    }

    private ParsedDirectoryWrapper<Schema> parseDirectory(File directory, File rootSchema) {
        Set<File> typesToAdd = Arrays
                .stream(Objects.requireNonNull(
                        directory.listFiles((dir, name) -> name.endsWith(AVRO_SCHEMA_EXTENSION))))
                .filter(file -> !file.getName().equals(rootSchema.getName())).collect(Collectors.toSet());

        Map<String, Schema> processed = new HashMap<>();
        Map<String, TypedContent> schemaContents = new HashMap<>();

        Schema.Parser rootSchemaParser = new Schema.Parser();
        Schema.Parser partialParser = new Schema.Parser();

        while (processed.size() != typesToAdd.size()) {
            boolean fileParsed = false;
            for (File typeToAdd : typesToAdd) {
                if (typeToAdd.getName().equals(rootSchema.getName())) {
                    continue;
                }
                try {
                    final ContentHandle schemaContent = readSchemaContent(typeToAdd);
                    final String contentType = ContentTypes.APPLICATION_JSON;
                    final TypedContent typedSchemaContent = TypedContent.create(schemaContent, contentType);
                    final Schema schema = partialParser.parse(schemaContent.content());
                    processed.put(schema.getFullName(), schema);
                    schemaContents.put(schema.getFullName(), typedSchemaContent);
                    fileParsed = true;
                } catch (SchemaParseException ex) {
                    log.warn(
                            "Error processing Avro schema with name {}. This usually means that the references are not ready yet to parse it",
                            typeToAdd.getName());
                }
            }
            partialParser = new Schema.Parser();
            partialParser.addTypes(processed);

            // If no schema has been processed during this iteration, that means there is an error in the
            // configuration, throw exception.
            if (!fileParsed) {
                throw new IllegalStateException(
                        "Error found in the directory structure. Check that all required files are present.");
            }

        }

        rootSchemaParser.addTypes(processed);

        return new AvroSchemaWrapper(rootSchemaParser.parse(readSchemaContent(rootSchema).content()),
                schemaContents);
    }

    private boolean isArrayWithSubschemaElement(Schema.Field field) {
        return field.schema().getType() == Schema.Type.ARRAY
                && field.schema().getElementType().getType() == Schema.Type.RECORD;
    }

    public static class AvroSchemaWrapper implements ParsedDirectoryWrapper<Schema> {
        final Schema schema;
        final Map<String, TypedContent> fileContents; // Original file contents from the file system.

        public AvroSchemaWrapper(Schema schema, Map<String, TypedContent> fileContents) {
            this.schema = schema;
            this.fileContents = fileContents;
        }

        @Override
        public Schema getSchema() {
            return schema;
        }

        @Override
        public Map<String, TypedContent> getSchemaContents() {
            return fileContents;
        }
    }
}