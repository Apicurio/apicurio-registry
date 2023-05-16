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


import io.apicurio.registry.content.ContentHandle;
import org.apache.avro.Schema;
import org.apache.avro.SchemaParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class AvroDirectoryParser {

    private static final String AVRO_SCHEMA_EXTENSION = ".avsc";
    private static final Logger log = LoggerFactory.getLogger(AvroDirectoryParser.class);

    public static AvroSchemaWrapper parse(File rootSchemaFile) {
        return parseDirectory(rootSchemaFile.getParentFile(), rootSchemaFile);
    }

    private static ContentHandle readSchemaContent(File schemaFile) {
        try {
            return ContentHandle.create(Files.readAllBytes(schemaFile.toPath()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read schema file: " + schemaFile, e);
        }
    }

    private static AvroSchemaWrapper parseDirectory(File directory, File rootSchema) {
        Set<File> typesToAdd = Arrays.stream(Objects.requireNonNull(directory.listFiles((dir, name) -> name.endsWith(AVRO_SCHEMA_EXTENSION))))
                .filter(file -> !file.getName().equals(rootSchema.getName())).collect(Collectors.toSet());

        Map<String, Schema> processed = new HashMap<>();
        Map<String, ContentHandle> schemaContents = new HashMap<>();

        Schema.Parser rootSchemaParser = new Schema.Parser();
        Schema.Parser partialParser = new Schema.Parser();

        while (processed.size() != typesToAdd.size()) {
            for (File typeToAdd : typesToAdd) {
                if (typeToAdd.getName().equals(rootSchema.getName())) {
                    continue;
                }
                try {
                    final ContentHandle schemaContent = readSchemaContent(typeToAdd);
                    final Schema schema = partialParser.parse(schemaContent.content());
                    processed.put(schema.getFullName(), schema);
                    schemaContents.put(schema.getFullName(), schemaContent);
                } catch (SchemaParseException ex) {
                    log.warn("Error processing Avro schema with name {}. This usually means that the references are not ready yet to parse it", typeToAdd.getName());
                }
            }
            partialParser = new Schema.Parser();
            partialParser.addTypes(processed);
        }

        rootSchemaParser.addTypes(processed);

        return new AvroSchemaWrapper(rootSchemaParser.parse(readSchemaContent(rootSchema).content()), schemaContents);
    }

    public static class AvroSchemaWrapper {
        final Schema schema;
        final Map<String, ContentHandle> fileContents; //Original file contents from the file system.

        public AvroSchemaWrapper(Schema schema, Map<String, ContentHandle> fileContents) {
            this.schema = schema;
            this.fileContents = fileContents;
        }

        public Schema getSchema() {
            return schema;
        }

        public Map<String, ContentHandle> getFileContents() {
            return fileContents;
        }
    }
}