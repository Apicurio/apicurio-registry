/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.content.dereference;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import io.apicurio.registry.content.ContentHandle;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.everit.json.schema.loader.SpecificationVersion;
import org.everit.json.schema.loader.internal.ReferenceResolver;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

/**
 * @author carnalca@redhat.com
 */
public class JsonSchemaDereferencer implements ContentDereferencer {

    private static final ObjectMapper objectMapper;
    private static final String SCHEMA_KEYWORD = "$schema";

    static {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new ParameterNamesModule());
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.registerModule(new JsonOrgModule());
        objectMapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        objectMapper.disable(FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
    }

    @Override
    public ContentHandle dereference(ContentHandle content, Map<String, ContentHandle> resolvedReferences) {
        try {
            JsonNode jsonNode = objectMapper.readTree(content.content());
            Schema schemaObj;
            // Extract the $schema to use for determining the id keyword
            SpecificationVersion spec = SpecificationVersion.DRAFT_7;
            if (jsonNode.has(SCHEMA_KEYWORD)) {
                String schema = jsonNode.get(SCHEMA_KEYWORD).asText();
                if (schema != null) {
                    spec = SpecificationVersion.lookupByMetaSchemaUrl(schema)
                            .orElse(SpecificationVersion.DRAFT_7);
                }
            }
            // Extract the $id to use for resolving relative $ref URIs
            URI idUri = null;
            if (jsonNode.has(spec.idKeyword())) {
                String id = jsonNode.get(spec.idKeyword()).asText();
                if (id != null) {
                    idUri = ReferenceResolver.resolve((URI) null, id);
                }
            }
            SchemaLoader.SchemaLoaderBuilder builder = SchemaLoader.builder()
                    .useDefaults(true).draftV7Support();
            for (Map.Entry<String, ContentHandle> dep : resolvedReferences.entrySet()) {
                URI child = ReferenceResolver.resolve(idUri, dep.getKey());
                builder.registerSchemaByURI(child, new JSONObject(dep.getValue().content()));
            }
            JSONObject jsonObject = objectMapper.treeToValue((jsonNode), JSONObject.class);
            builder.schemaJson(jsonObject);
            SchemaLoader loader = builder.build();
            schemaObj = loader.load().build();
            return ContentHandle.create(schemaObj.toString());
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid JSON " + content.content(), e);
        }
    }
}
