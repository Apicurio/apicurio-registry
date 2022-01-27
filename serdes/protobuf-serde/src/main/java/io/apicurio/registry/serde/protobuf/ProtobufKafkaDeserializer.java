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

package io.apicurio.registry.serde.protobuf;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Headers;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.utils.Utils;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.AbstractKafkaDeserializer;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchema;
import io.apicurio.registry.serde.protobuf.ref.RefOuterClass.Ref;

/**
 * @author Ales Justin
 * @author Hiram Chirino
 * @author Fabian Martinez
 */
public class ProtobufKafkaDeserializer<U extends Message> extends AbstractKafkaDeserializer<ProtobufSchema, U> {

    private static final String PROTOBUF_PARSE_METHOD = "parseFrom";

    private ProtobufSchemaParser<U> parser = new ProtobufSchemaParser<>();

    private Class<?> specificReturnClass;
    private Method specificReturnClassParseMethod;
    private boolean deriveClass;

    private Map<String, Method> parseMethodsCache = new ConcurrentHashMap<>();

    private ProtobufSerdeHeaders serdeHeaders;


    public ProtobufKafkaDeserializer() {
        super();
    }

    public ProtobufKafkaDeserializer(RegistryClient client,
            SchemaResolver<ProtobufSchema, U> schemaResolver) {
        super(client, schemaResolver);
    }

    public ProtobufKafkaDeserializer(RegistryClient client) {
        super(client);
    }

    public ProtobufKafkaDeserializer(SchemaResolver<ProtobufSchema, U> schemaResolver) {
        super(schemaResolver);
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        ProtobufKafkaDeserializerConfig config = new ProtobufKafkaDeserializerConfig(configs, isKey);
        super.configure(config, isKey);

        specificReturnClass = config.getSpecificReturnClass();
        try {
            if (specificReturnClass != null) {
                if (specificReturnClass.equals(DynamicMessage.class)) {
                    this.specificReturnClassParseMethod = specificReturnClass.getDeclaredMethod(PROTOBUF_PARSE_METHOD, Descriptor.class, InputStream.class);
                } else if (!specificReturnClass.equals(Object.class)) {
                    this.specificReturnClassParseMethod = specificReturnClass.getDeclaredMethod(PROTOBUF_PARSE_METHOD, InputStream.class);
                } else {
                    throw new ConfigException("Class " + specificReturnClass.getCanonicalName() + " is not a valid protobuf message class");
                }
            }
        } catch (Exception e) {
            throw new ConfigException("Class " + specificReturnClass.getCanonicalName() + " is not a valid protobuf message class", e);
        }

        deriveClass = config.deriveClass();

        serdeHeaders = new ProtobufSerdeHeaders(new HashMap<>(configs), isKey);
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerDe#schemaParser()
     */
    @Override
    public SchemaParser<ProtobufSchema, U> schemaParser() {
        return parser;
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaDeserializer#readData(org.apache.kafka.common.header.Headers, io.apicurio.registry.serde.ParsedSchema, java.nio.ByteBuffer, int, int)
     */
    @Override
    protected U readData(Headers headers, ParsedSchema<ProtobufSchema> schema, ByteBuffer buffer, int start, int length) {
        return internalReadData(headers, schema, buffer, start, length);
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaDeserializer#readData(io.apicurio.registry.serde.ParsedSchema, java.nio.ByteBuffer, int, int)
     */
    @Override
    protected U readData(ParsedSchema<ProtobufSchema> schema, ByteBuffer buffer, int start, int length) {
        return internalReadData(null, schema, buffer, start, length);
    }

    @SuppressWarnings("unchecked")
    protected U internalReadData(Headers headers, ParsedSchema<ProtobufSchema> schema, ByteBuffer buff, int start, int length) {
        try {
            byte[] bytes = new byte[length];
            System.arraycopy(buff.array(), start, bytes, 0, length);

            ByteArrayInputStream is = new ByteArrayInputStream(bytes);

            Descriptor descriptor = null;
            if (headers != null) {
                String messageTypeName = serdeHeaders.getProtobufTypeName(headers);
                if (messageTypeName != null) {
                    descriptor = schema.getParsedSchema().getFileDescriptor().findMessageTypeByName(messageTypeName);
                }
            }
            if (descriptor == null){
                try {
                    Ref ref = Ref.parseDelimitedFrom(is);
                    descriptor = schema.getParsedSchema().getFileDescriptor().findMessageTypeByName(ref.getName());
                } catch (IOException e) {
                    is = new ByteArrayInputStream(bytes);
                    //use the first message type found
                    descriptor = schema.getParsedSchema().getFileDescriptor().getMessageTypes().get(0);
                }
            }

            if (specificReturnClassParseMethod != null) {
                try {
                    if (specificReturnClass.equals(DynamicMessage.class)) {
                        return (U) specificReturnClassParseMethod.invoke(null, descriptor, is);
                    }
                    return (U) specificReturnClassParseMethod.invoke(null, is);
                } catch (Exception e) {
                    throw new ConfigException("Not a valid protobuf builder", e);
                }
            } else if (deriveClass) {
                String className = deriveClassFromDescriptor(descriptor);
                if (className != null) {
                    return invokeParseMethod(is, className);
                }
            } else if (headers != null) {
                String messageTypeHeader = serdeHeaders.getMessageType(headers);
                if (messageTypeHeader != null) {
                    return invokeParseMethod(is, messageTypeHeader);
                }
            }

            return (U) DynamicMessage.parseFrom(descriptor, is);

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private U invokeParseMethod(InputStream buffer, String className) {
        try {
            Method parseMethod = parseMethodsCache.computeIfAbsent(className, k -> {
                Class<?> protobufClass = Utils.loadClass(className);
                try {
                    return protobufClass.getDeclaredMethod(PROTOBUF_PARSE_METHOD, InputStream.class);
                } catch (NoSuchMethodException | SecurityException e) {
                    throw new SerializationException("Class " + className + " is not a valid protobuf message class", e);
                }
            });
            return (U) parseMethod.invoke(null, buffer);
        } catch (IllegalAccessException | InvocationTargetException e) {
            parseMethodsCache.remove(className);
            throw new SerializationException("Not a valid protobuf builder", e);
        }
    }

    //TODO refactor
    public String deriveClassFromDescriptor(Descriptor des) {
        Descriptor descriptor = des;
        FileDescriptor fd = descriptor.getFile();
        DescriptorProtos.FileOptions o = fd.getOptions();
        String p = o.hasJavaPackage() ? o.getJavaPackage() : fd.getPackage();
        String outer = "";
        if (!o.getJavaMultipleFiles()) {
            if (o.hasJavaOuterClassname()) {
                outer = o.getJavaOuterClassname();
            } else {
                // Can't determine full name without either java_outer_classname or java_multiple_files
                return null;
            }
        }
        StringBuilder inner = new StringBuilder();
        while (descriptor != null) {
            if (inner.length() == 0) {
                inner.insert(0, descriptor.getName());
            } else {
                inner.insert(0, descriptor.getName() + "$");
            }
            descriptor = descriptor.getContainingType();
        }
        String d1 = (!outer.isEmpty() || inner.length() != 0 ? "." : "");
        String d2 = (!outer.isEmpty() && inner.length() != 0 ? "$" : "");
        return p + d1 + outer + d2 + inner;
      }

}
