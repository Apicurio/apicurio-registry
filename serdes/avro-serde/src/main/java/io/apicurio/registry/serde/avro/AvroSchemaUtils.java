/*
 * Copyright Confluent Inc
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

package io.apicurio.registry.serde.avro;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.utils.IoUtil;
import org.apache.avro.Schema;
import org.apache.avro.SchemaParseException;
import org.apache.avro.generic.GenericContainer;
import org.apache.avro.reflect.ReflectData;
import org.apache.kafka.common.errors.SerializationException;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Confluent Inc.
 * @author Ales Justin
 * @author Carles Arnal
 */
public class AvroSchemaUtils {

    private static final Map<String, Schema> primitiveSchemas;

    static {
        Schema.Parser parser = new Schema.Parser();
        primitiveSchemas = new HashMap<>();
        primitiveSchemas.put("Null", createPrimitiveSchema(parser, "null"));
        primitiveSchemas.put("Boolean", createPrimitiveSchema(parser, "boolean"));
        primitiveSchemas.put("Integer", createPrimitiveSchema(parser, "int"));
        primitiveSchemas.put("Long", createPrimitiveSchema(parser, "long"));
        primitiveSchemas.put("Float", createPrimitiveSchema(parser, "float"));
        primitiveSchemas.put("Double", createPrimitiveSchema(parser, "double"));
        primitiveSchemas.put("String", createPrimitiveSchema(parser, "string"));
        primitiveSchemas.put("Bytes", createPrimitiveSchema(parser, "bytes"));
    }

    private static Schema createPrimitiveSchema(Schema.Parser parser, String type) {
        String schemaString = String.format("{\"type\" : \"%s\"}", type);
        return parser.parse(schemaString);
    }

    public static Schema parse(String schema) {
        return parse(schema, Collections.emptyList());
    }

    public static Schema parse(String schema, List<ParsedSchema<Schema>> references) {
        //First try to parse without references, useful when the content is dereferenced
        try {
            final Schema.Parser parser = new Schema.Parser();
            return parser.parse(schema);
        } catch (SchemaParseException e) {
            //If we fail to parse the content from the main schema, then parse first the references and then the main schema
            final Schema.Parser parser = new Schema.Parser();
            handleReferences(parser, references);
            return parser.parse(schema);
        }
    }

    private static void handleReferences(Schema.Parser parser, List<ParsedSchema<Schema>> schemaReferences) {
        for (ParsedSchema<Schema> referencedContent : schemaReferences) {
            if (referencedContent.hasReferences()) {
                handleReferences(parser, referencedContent.getSchemaReferences());
            }
            if (!parser.getTypes().containsKey(referencedContent.getParsedSchema().getFullName())) {
                parser.parse(IoUtil.toString(referencedContent.getRawSchema()));
            }
        }
    }

    public static boolean isPrimitive(Schema schema) {
        return primitiveSchemas.containsValue(schema);
    }

    static Schema getReflectSchema(Object object) {
        Class<?> clazz = (object instanceof Class) ? (Class<?>) object : object.getClass();
        Schema schema = ReflectData.get().getSchema(clazz);
        if (schema == null) {
            throw new SerializationException("No schema for class: " + clazz.getName());
        }
        return schema;
    }

    static Schema getSchema(Object object) {
        return getSchema(object, false);
    }

    static Schema getSchema(Object object, boolean useReflection) {
        if (object == null) {
            return primitiveSchemas.get("Null");
        } else if (object instanceof Boolean) {
            return primitiveSchemas.get("Boolean");
        } else if (object instanceof Integer) {
            return primitiveSchemas.get("Integer");
        } else if (object instanceof Long) {
            return primitiveSchemas.get("Long");
        } else if (object instanceof Float) {
            return primitiveSchemas.get("Float");
        } else if (object instanceof Double) {
            return primitiveSchemas.get("Double");
        } else if (object instanceof CharSequence) {
            return primitiveSchemas.get("String");
        } else if (object instanceof byte[] || object instanceof ByteBuffer) {
            return primitiveSchemas.get("Bytes");
        } else if (useReflection) {
            Schema schema = ReflectData.get().getSchema(object.getClass());
            if (schema == null) {
                throw new SerializationException("Schema is null for object of class " + object.getClass()
                        .getCanonicalName());
            } else {
                return schema;
            }
        } else if (object instanceof GenericContainer) {
            return ((GenericContainer) object).getSchema();
        } else if (object instanceof Map) {
            // This case is unusual -- the schema isn't available directly anywhere, instead we have to
            // take get the value schema out of one of the entries and then construct the full schema.
            @SuppressWarnings("rawtypes")
            Map mapValue = ((Map) object);
            if (mapValue.isEmpty()) {
                // In this case the value schema doesn't matter since there is no content anyway. This
                // only works because we know in this case that we are only using this for conversion and
                // no data will be added to the map.
                return Schema.createMap(primitiveSchemas.get("Null"));
            }
            Schema valueSchema = getSchema(mapValue.values().iterator().next());
            return Schema.createMap(valueSchema);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported Avro type. Supported types are null, Boolean, Integer, Long, "
                            + "Float, Double, String, byte[] and IndexedRecord");
        }
    }
}