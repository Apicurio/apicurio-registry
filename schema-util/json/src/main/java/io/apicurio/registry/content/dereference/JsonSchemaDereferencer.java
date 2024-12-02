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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.refs.JsonPointerExternalReference;
import io.vertx.core.json.JsonObject;
import io.vertx.json.schema.Draft;
import io.vertx.json.schema.JsonSchema;
import io.vertx.json.schema.JsonSchemaOptions;
import io.vertx.json.schema.impl.JsonRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

/**
 * @author carnalca@redhat.com
 */
public class JsonSchemaDereferencer implements ContentDereferencer {

    private static final ObjectMapper objectMapper;
    private static final Logger log = LoggerFactory.getLogger(JsonSchemaDereferencer.class);
    private static final String idKey = "$id";
    private static final String schemaKey = "$schema";

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
        //Here, when using rewrite, I need the new reference coordinates, using the full artifact coordinates
        // and not just the reference name and the old name, to be able to do the re-write.
        String id = null;
        String schema = null;

        try {
            JsonNode contentNode = objectMapper.readTree(content.content());
            id = contentNode.get(idKey).asText();
            schema = contentNode.get(schemaKey).asText();
        }
        catch (JsonProcessingException e) {
            log.warn("No schema or id provided for schema");
        }

        JsonSchemaOptions jsonSchemaOptions = new JsonSchemaOptions()
                .setBaseUri("http://localhost");

        if (null != schema) {
            jsonSchemaOptions.setDraft(Draft.fromIdentifier(schema));
        }

        Map<String, JsonSchema> lookups = new HashMap<>();
        resolveReferences(resolvedReferences, lookups);
        JsonObject resolvedSchema = JsonRef.resolve(new JsonObject(content.content()), lookups);

        if (null != id) {
            resolvedSchema.put(idKey, id);
        }

        if (schema != null) {
            resolvedSchema.put(schemaKey, schema);
        }

        return ContentHandle.create(resolvedSchema.encodePrettily());
    }

    private void resolveReferences(Map<String, ContentHandle> resolvedReferences, Map<String, JsonSchema> lookups) {
        resolvedReferences.forEach((referenceName, schema) -> {
            JsonPointerExternalReference externalRef = new JsonPointerExternalReference(referenceName);
            // Note: when adding to 'lookups', strip away the "component" part of the reference, because the
            // vertx library is going to do the lookup ONLY by the resource name, excluding the component
            lookups.computeIfAbsent(externalRef.getResource(), (key) -> {
                JsonObject resolvedSchema = JsonRef.resolve(new JsonObject(schema.content()), lookups);
                return JsonSchema.of(resolvedSchema);
            });
        });
    }

    /**
     * @see io.apicurio.registry.content.dereference.ContentDereferencer#rewriteReferences(io.apicurio.registry.content.ContentHandle, java.util.Map)
     */
    @Override
    public ContentHandle rewriteReferences(ContentHandle content, Map<String, String> resolvedReferenceUrls) {
        try {
            JsonNode tree = objectMapper.readTree(content.content());
            rewriteIn(tree, resolvedReferenceUrls);
            String converted = objectMapper.writeValueAsString(objectMapper.treeToValue(tree, Object.class));
            return ContentHandle.create(converted);
        }
        catch (Exception e) {
            return content;
        }
    }

    private void rewriteIn(JsonNode node, Map<String, String> resolvedReferenceUrls) {
        if (node.isObject()) {
            rewriteInObject((ObjectNode) node, resolvedReferenceUrls);
        } else if (node.isArray()) {
            rewriteInArray((ArrayNode) node, resolvedReferenceUrls);
        }
    }

    private void rewriteInObject(ObjectNode node, Map<String, String> resolvedReferenceUrls) {
        if (node.hasNonNull("$ref")) {
            String $ref = node.get("$ref").asText();
            if (resolvedReferenceUrls.containsKey($ref)) {
                node.put("$ref", resolvedReferenceUrls.get($ref));
            } else {
                //The reference in the file might be using just a component, use just the resource for the lookup.
                JsonPointerExternalReference externalReference = new JsonPointerExternalReference($ref);
                if (resolvedReferenceUrls.containsKey(externalReference.getResource())) {
                    JsonPointerExternalReference rewrittenRef = new JsonPointerExternalReference(resolvedReferenceUrls.get(externalReference.getResource()), externalReference.getComponent());
                    node.put("$ref", rewrittenRef.getFullReference());
                }
            }
        }
        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode fieldValue = node.get(fieldName);
            if (fieldValue.isObject()) {
                rewriteInObject((ObjectNode) fieldValue, resolvedReferenceUrls);
            } else if (fieldValue.isArray()) {
                rewriteInArray((ArrayNode) fieldValue, resolvedReferenceUrls);
            }
        }
    }

    private void rewriteInArray(ArrayNode node, Map<String, String> resolvedReferenceUrls) {
        node.forEach(innerNode -> {
            rewriteIn(innerNode, resolvedReferenceUrls);
        });
    }
}
