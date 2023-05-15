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


import org.apache.avro.Schema;
import org.apache.avro.SchemaParseException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class AvroDirectoryParser {

    private static final String AVRO_SCHEMA_EXTENSION = ".avsc";

    public static Schema parse(File rootSchemaFile) {
        return parseDirectory(rootSchemaFile.getParentFile(), rootSchemaFile);
    }

    private static String readSchemaContent(File schemaFile) {
        try {
            return new String(Files.readAllBytes(schemaFile.toPath()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read schema file: " + schemaFile, e);
        }
    }

    private static Schema parseDirectory(File directory, File rootSchema) {
        Set<File> schemaFiles = Arrays.stream(Objects.requireNonNull(directory.listFiles((dir, name) -> name.endsWith(AVRO_SCHEMA_EXTENSION))))
                .collect(Collectors.toSet());

        schemaFiles.remove(rootSchema);

        Set<File> typesToAdd = new HashSet<>(schemaFiles);

        Map<String, Schema> processed = new HashMap<>();

        Schema.Parser rootSchemaParser = new Schema.Parser();
        Schema.Parser partialParser = new Schema.Parser();
        while (processed.size() != typesToAdd.size()) {
            for (File typeToAdd: typesToAdd) {
                if (typeToAdd.getName().equals(rootSchema.getName())) {
                   continue;
                }
                try {
                    final Schema schema = partialParser.parse(readSchemaContent(typeToAdd));
                    processed.put(schema.getFullName(), schema);
                } catch (SchemaParseException ex) {
                }
            }
            partialParser = new Schema.Parser();
            partialParser.addTypes(processed);
        }

        rootSchemaParser.addTypes(processed);

        return rootSchemaParser.parse(readSchemaContent(rootSchema));
    }
}