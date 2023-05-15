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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class JsonSchemaDirectoryParser {

    private static final String JSON_EXTENSION = ".json";

    private final File directory;
    private final ObjectMapper mapper;
    private final List<JsonNode> schemas;

    public JsonSchemaDirectoryParser(File directory) {
        this.directory = directory;
        this.mapper = new ObjectMapper();
        this.schemas = new ArrayList<>();
    }

    public List<JsonNode> parse() throws IOException {
        for (File file : directory.listFiles()) {
            if (file.isFile() && file.getName().endsWith(JSON_EXTENSION)) {
                String json = new String(Files.readAllBytes(Paths.get(file.getPath())));
                JsonNode node = mapper.readTree(json);
                parseNode(node);
            }
        }
        return schemas;
    }

    private void parseNode(JsonNode node) {
        if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (JsonNode item : array) {
                parseNode(item);
            }
        } else if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            if (object.has("$ref")) {
                String ref = object.get("$ref").asText();
                String[] parts = ref.split("/");
                String name = parts[parts.length - 1];
                JsonNode schema = findOrCreateSchema(name);
                object.putAll((ObjectNode) schema);
            } else {
                for (JsonNode child : node) {
                    parseNode(child);
                }
            }
        }
    }

    private JsonNode findOrCreateSchema(String name) {
        for (JsonNode schema : schemas) {
            if (schema.get("title").asText().equals(name)) {
                return schema;
            }
        }
        String path = directory.getPath() + File.separator + name + JSON_EXTENSION;
        String json = null;
        try {
            json = new String(Files.readAllBytes(Paths.get(path)));
        } catch (IOException e) {
            e.printStackTrace();
        }
        JsonNode schema = null;
        try {
            schema = mapper.readTree(json);
            schemas.add(schema);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return schema;
    }
}

