/*
 * Copyright 2022 Red Hat
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

package io.apicurio.registry.serde.jsonschema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BinaryNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.NumericNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.everit.json.schema.ArraySchema;
import org.everit.json.schema.ObjectSchema;
import org.everit.json.schema.ReferenceSchema;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.everit.json.schema.loader.SpecificationVersion;
import org.everit.json.schema.loader.internal.ReferenceResolver;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * @author Carles Arnal
 */

public class JsonSchema {
    public static final String TYPE = "JSON";
    private static final String SCHEMA_KEYWORD = "$schema";
    private static final Object NONE_MARKER = new Object();
    private final JsonNode jsonNode;
    private transient Schema schemaObj;
    private final Integer version;
    private final Map<String, JsonSchema> resolvedReferences;
    private transient String canonicalString;
    private transient int hashCode;
    private static final int NO_HASHCODE = -2147483648;
    private static final ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public JsonSchema(JsonNode jsonNode) {
        this(jsonNode, Collections.emptyMap(), null);
    }

    public JsonSchema(String schemaString) {
        this(schemaString, Collections.emptyMap(), null);
    }

    public JsonSchema(JsonNode jsonNode, Map<String, JsonSchema> resolvedReferences, Integer version) {
        this.hashCode = -2147483648;
        this.jsonNode = jsonNode;
        this.version = version;
        this.resolvedReferences = Collections.unmodifiableMap(resolvedReferences);
    }

    public JsonSchema(String schemaString, Map<String, JsonSchema> resolvedReferences, Integer version) {
        this.hashCode = -2147483648;

        try {
            this.jsonNode = objectMapper.readTree(schemaString);
            this.version = version;
            this.resolvedReferences = Collections.unmodifiableMap(resolvedReferences);
        } catch (IOException var6) {
            throw new IllegalArgumentException("Invalid JSON " + schemaString, var6);
        }
    }

    public JsonSchema(Schema schemaObj) {
        this(schemaObj, null);
    }

    public JsonSchema(Schema schemaObj, Integer version) {
        this.hashCode = -2147483648;

        try {
            this.jsonNode = schemaObj != null ? objectMapper.readTree(schemaObj.toString()) : null;
            this.schemaObj = schemaObj;
            this.version = version;
            this.resolvedReferences = Collections.emptyMap();
        } catch (IOException var4) {
            throw new IllegalArgumentException("Invalid JSON " + schemaObj, var4);
        }
    }

    private JsonSchema(JsonNode jsonNode, Schema schemaObj, Integer version, Map<String, JsonSchema> resolvedReferences, String canonicalString) {
        this.hashCode = -2147483648;
        this.jsonNode = jsonNode;
        this.schemaObj = schemaObj;
        this.version = version;
        this.resolvedReferences = resolvedReferences;
        this.canonicalString = canonicalString;
    }

    public JsonSchema copy() {
        return new JsonSchema(this.jsonNode, this.schemaObj, this.version, this.resolvedReferences, this.canonicalString);
    }

    public JsonSchema copy(Integer version) {
        return new JsonSchema(this.jsonNode, this.schemaObj, version, this.resolvedReferences, this.canonicalString);
    }

    public JsonNode toJsonNode() {
        return this.jsonNode;
    }

    public Schema rawSchema() {
        if (this.jsonNode == null) {
            return null;
        } else {
            if (this.schemaObj == null) {
                SpecificationVersion spec = SpecificationVersion.DRAFT_7;
                if (this.jsonNode.has(SCHEMA_KEYWORD)) {
                    String schema = this.jsonNode.get(SCHEMA_KEYWORD).asText();
                    if (schema != null) {
                        spec = SpecificationVersion.lookupByMetaSchemaUrl(schema).orElse(SpecificationVersion.DRAFT_7);
                    }
                }

                URI idUri = null;
                if (this.jsonNode.has(spec.idKeyword())) {
                    String id = this.jsonNode.get(spec.idKeyword()).asText();
                    if (id != null) {
                        idUri = ReferenceResolver.resolve((URI) null, id);
                    }
                }

                SchemaLoader.SchemaLoaderBuilder builder = SchemaLoader.builder().useDefaults(true).draftV7Support();

                for (Map.Entry<String, JsonSchema> stringStringEntry : this.resolvedReferences.entrySet()) {
                    URI child = ReferenceResolver.resolve(idUri, stringStringEntry.getKey());
                    builder.registerSchemaByURI(child, new JSONObject(stringStringEntry.getValue()));
                }

                JSONObject jsonObject = new JSONObject(this.canonicalString());
                builder.schemaJson(jsonObject);
                SchemaLoader loader = builder.build();
                this.schemaObj = loader.load().build();

            }
            return this.schemaObj;
        }

    }

    public String schemaType() {
        return "JSON";
    }

    public String name() {
        return this.getString("title");
    }

    public String getString(String key) {
        return this.jsonNode.has(key) ? this.jsonNode.get(key).asText() : null;
    }

    public String canonicalString() {
        if (this.jsonNode == null) {
            return null;
        } else {
            if (this.canonicalString == null) {
                try {
                    this.canonicalString = objectMapper.writeValueAsString(this.jsonNode);
                } catch (IOException var2) {
                    throw new IllegalArgumentException("Invalid JSON", var2);
                }
            }

            return this.canonicalString;
        }
    }

    public Integer version() {
        return this.version;
    }

    public Map<String, JsonSchema> resolvedReferences() {
        return this.resolvedReferences;
    }

    public void validate(Object value) throws JsonProcessingException, ValidationException {
        Object primitiveValue = NONE_MARKER;
        if (isPrimitive(value)) {
            primitiveValue = value;
        } else if (value instanceof BinaryNode) {
            primitiveValue = ((BinaryNode) value).asText();
        } else if (value instanceof BooleanNode) {
            primitiveValue = ((BooleanNode) value).asBoolean();
        } else if (value instanceof NullNode) {
            primitiveValue = null;
        } else if (value instanceof NumericNode) {
            primitiveValue = ((NumericNode) value).numberValue();
        } else if (value instanceof TextNode) {
            primitiveValue = ((TextNode) value).asText();
        }

        if (primitiveValue != NONE_MARKER) {
            this.rawSchema().validate(primitiveValue);
        } else {
            Object jsonObject;
            if (value instanceof ArrayNode) {
                jsonObject = objectMapper.treeToValue((ArrayNode) value, JSONArray.class);
            } else if (value instanceof ObjectNode) {
                jsonObject = new JSONObject(value.toString());
            } else if (value instanceof JsonNode) {
                jsonObject = objectMapper.treeToValue((JsonNode) value, JSONObject.class);
            } else if (value.getClass().isArray()) {
                jsonObject = objectMapper.convertValue(value, JSONArray.class);
            } else if (value instanceof JSONObject) {
                jsonObject = value;
            } else {
                jsonObject = objectMapper.convertValue(value, JSONObject.class);
            }

            this.rawSchema().validate(jsonObject);

            if (this.schemaObj instanceof ObjectSchema && jsonObject instanceof JSONObject) {
                for (Map.Entry<String, Schema> schema : ((ObjectSchema) schemaObj).getPropertySchemas().entrySet()) {
                    if (schema.getValue() instanceof ReferenceSchema) {
                        validateComplexObject((JSONObject) jsonObject, schema);
                    } else if (isArrayWithComplexType(schema) && ((JSONObject) jsonObject).has(schema.getKey())) {
                        validateArrayProperty(((JSONObject) jsonObject).getJSONArray(schema.getKey()), ((ArraySchema) schema.getValue()).getAllItemSchema());
                    }
                }
            }
        }
    }

    private void validateComplexObject(JSONObject jsonObject, Map.Entry<String, Schema> schema) throws JsonProcessingException {
        if (isRequiredProperty(schema) || objectContainsSchemaKey(jsonObject, schema)) {
            resolvedReferences.get(((ReferenceSchema) schema.getValue()).getReferenceValue()).validate(jsonObject.get(schema.getKey()));
        }
    }

    private void validateArrayProperty(JSONArray arrayProperty, Schema schema) throws JsonProcessingException {
        for (int i = 0; i < arrayProperty.length(); i++) {
            resolvedReferences.get(((ReferenceSchema) schema).getReferenceValue()).validate(arrayProperty.getJSONObject(i));
        }
    }

    private boolean isArrayWithComplexType(Map.Entry<String, Schema> schema) {
        return schema.getValue() instanceof ArraySchema && ((ArraySchema) schema.getValue()).getAllItemSchema() instanceof ReferenceSchema;
    }

    private boolean objectContainsSchemaKey(JSONObject jsonObject, Map.Entry<String, Schema> schema) {
        return jsonObject.has(schema.getKey());
    }

    private boolean isRequiredProperty(Map.Entry<String, Schema> schema) {
        return ((ObjectSchema) schemaObj).getRequiredProperties().contains(schema.getKey());
    }

    private static boolean isPrimitive(Object value) {
        return value == null || value instanceof Boolean || value instanceof Number || value instanceof String;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            JsonSchema that = (JsonSchema) o;
            return Objects.equals(this.jsonNode, that.jsonNode) && Objects.equals(this.resolvedReferences, that.resolvedReferences) && Objects.equals(this.version, that.version);
        } else {
            return false;
        }
    }

    public int hashCode() {
        if (this.hashCode == -2147483648) {
            this.hashCode = Objects.hash(this.jsonNode, this.resolvedReferences, this.version);
        }

        return this.hashCode;
    }

    public String toString() {
        return this.canonicalString();
    }
}
