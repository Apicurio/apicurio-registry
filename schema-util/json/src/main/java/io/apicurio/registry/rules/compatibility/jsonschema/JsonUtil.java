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

package io.apicurio.registry.rules.compatibility.jsonschema;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import io.apicurio.registry.content.ContentHandle;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.everit.json.schema.loader.SpecificationVersion;
import org.everit.json.schema.loader.internal.ReferenceResolver;
import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;

import java.net.URI;
import java.util.Collections;
import java.util.Map;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

/**
 * @author Jakub Senko 'jsenko@redhat.com'
 */
public class JsonUtil {

    public static final ObjectMapper MAPPER;
    private static final String SCHEMA_KEYWORD = "$schema";

    static {
        MAPPER =  new ObjectMapper();
        MAPPER.registerModule(new JsonOrgModule());
        MAPPER.registerModule(new ParameterNamesModule());
        MAPPER.registerModule(new Jdk8Module());
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.registerModule(new JsonOrgModule());
        MAPPER.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        MAPPER.disable(FAIL_ON_UNKNOWN_PROPERTIES);
        MAPPER.setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
    }

    public static Schema readSchema(String content) throws JsonProcessingException {
        return readSchema(content, Collections.emptyMap());
    }

    public static Schema readSchema(String content, Map<String, ContentHandle> resolvedReferences) throws JsonProcessingException {
        JsonNode jsonNode = MAPPER.readTree(content);
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
        JSONObject jsonObject = MAPPER.treeToValue((jsonNode), JSONObject.class);
        builder.schemaJson(jsonObject);
        SchemaLoader loader = builder.build();
        schemaObj = loader.load().build();
        return schemaObj;
    }
}
