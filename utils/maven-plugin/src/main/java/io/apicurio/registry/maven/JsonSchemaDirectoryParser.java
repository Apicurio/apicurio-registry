/*
 * Copyright 2023 Red Hat
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

package io.apicurio.registry.maven;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rules.compatibility.jsonschema.JsonUtil;
import org.everit.json.schema.ArraySchema;
import org.everit.json.schema.ObjectSchema;
import org.everit.json.schema.ReferenceSchema;
import org.everit.json.schema.Schema;
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
import java.util.stream.Collectors;

public class JsonSchemaDirectoryParser extends AbstractDirectoryParser<Schema> {

    private static final String JSON_SCHEMA_EXTENSION = ".json";
    private static final Logger log = LoggerFactory.getLogger(JsonSchemaDirectoryParser.class);

    public JsonSchemaDirectoryParser(RegistryClient client) {
        super(client);
    }

    @Override
    public ParsedDirectoryWrapper<Schema> parse(File rootSchemaFile) {
        return parseDirectory(rootSchemaFile.getParentFile(), rootSchemaFile);
    }

    @Override
    public List<ArtifactReference> handleSchemaReferences(RegisterArtifact rootArtifact, org.everit.json.schema.Schema rootSchema, Map<String, ContentHandle> fileContents) throws FileNotFoundException {

        if (rootSchema instanceof ObjectSchema) {

            ObjectSchema objectSchema = (ObjectSchema) rootSchema;
            Set<ArtifactReference> references = new HashSet<>();

            Map<String, org.everit.json.schema.Schema> rootSchemaPropertySchemas = objectSchema.getPropertySchemas();

            for (String schemaKey : rootSchemaPropertySchemas.keySet()) {

                List<ArtifactReference> nestedArtifactReferences = new ArrayList<>();

                if (rootSchemaPropertySchemas.get(schemaKey) instanceof ReferenceSchema) {

                    ReferenceSchema nestedSchema = (ReferenceSchema) rootSchemaPropertySchemas.get(schemaKey);
                    RegisterArtifact nestedRegisterArtifact = buildFromRoot(rootArtifact, nestedSchema.getSchemaLocation());

                    if (nestedSchema.getReferredSchema() instanceof ObjectSchema) {
                        ObjectSchema nestedObjectSchema = (ObjectSchema) nestedSchema.getReferredSchema();
                        nestedArtifactReferences = handleSchemaReferences(nestedRegisterArtifact, nestedObjectSchema, fileContents);
                    }

                    references.add(registerNestedSchema(nestedSchema.getSchemaLocation(), nestedArtifactReferences, nestedRegisterArtifact, fileContents.get(nestedSchema.getSchemaLocation()).content()));

                } else if (rootSchemaPropertySchemas.get(schemaKey) instanceof ArraySchema) {

                    final ArraySchema arraySchema = (ArraySchema) rootSchemaPropertySchemas.get(schemaKey);
                    if (arraySchema.getAllItemSchema() instanceof ReferenceSchema) {

                        ReferenceSchema arrayElementSchema = (ReferenceSchema) arraySchema.getAllItemSchema();
                        RegisterArtifact nestedRegisterArtifact = buildFromRoot(rootArtifact, arrayElementSchema.getSchemaLocation());

                        if (arrayElementSchema.getReferredSchema() instanceof ObjectSchema) {

                            nestedArtifactReferences = handleSchemaReferences(nestedRegisterArtifact, arrayElementSchema, fileContents);
                        }
                        references.add(registerNestedSchema(arrayElementSchema.getSchemaLocation(), nestedArtifactReferences, nestedRegisterArtifact, fileContents.get(arrayElementSchema.getSchemaLocation()).content()));
                    }
                }
            }
            return new ArrayList<>(references);
        } else {
            return Collections.emptyList();
        }
    }

    private JsonSchemaWrapper parseDirectory(File directory, File rootSchema) {
        Set<File> typesToAdd = Arrays.stream(Objects.requireNonNull(directory.listFiles((dir, name) -> name.endsWith(JSON_SCHEMA_EXTENSION))))
                .filter(file -> !file.getName().equals(rootSchema.getName())).collect(Collectors.toSet());

        Map<String, Schema> processed = new HashMap<>();
        Map<String, ContentHandle> schemaContents = new HashMap<>();

        while (processed.size() != typesToAdd.size()) {
            boolean fileParsed = false;
            for (File typeToAdd : typesToAdd) {
                if (typeToAdd.getName().equals(rootSchema.getName())) {
                    continue;
                }
                try {
                    final ContentHandle schemaContent = readSchemaContent(typeToAdd);
                    final Schema schema = JsonUtil.readSchema(schemaContent.content(), schemaContents, false);
                    processed.put(schema.getId(), schema);
                    schemaContents.put(schema.getId(), schemaContent);
                    fileParsed = true;
                } catch (JsonProcessingException ex) {
                    log.warn("Error processing json schema with name {}. This usually means that the references are not ready yet to parse it", typeToAdd.getName());
                }
            }

            //If no schema has been processed during this iteration, that means there is an error in the configuration, throw exception.
            if (!fileParsed) {
                throw new IllegalStateException("Error found in the directory structure. Check that all required files are present.");
            }
        }

        try {
            return new JsonSchemaWrapper(JsonUtil.readSchema(readSchemaContent(rootSchema).content(), schemaContents, false), schemaContents);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Unable to parse main schema", e);
        }
    }

    public static class JsonSchemaWrapper implements ParsedDirectoryWrapper<Schema> {
        final Schema schema;
        final Map<String, ContentHandle> fileContents;

        public JsonSchemaWrapper(Schema schema, Map<String, ContentHandle> fileContents) {
            this.schema = schema;
            this.fileContents = fileContents;
        }

        @Override
        public Schema getSchema() {
            return schema;
        }

        @Override
        public Map<String, ContentHandle> getSchemaContents() {
            return fileContents;
        }
    }

}

