package io.apicurio.registry.serde.protobuf;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.resolver.utils.Utils;
import io.apicurio.registry.serde.AbstractDeserializer;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.protobuf.ref.RefOuterClass.Ref;
import io.apicurio.registry.serde.utils.ByteBufferInputStream;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchema;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProtobufDeserializer<U extends Message> extends AbstractDeserializer<ProtobufSchema, U> {

    private static final String PROTOBUF_PARSE_METHOD = "parseFrom";

    private final ProtobufSchemaParser<U> parser = new ProtobufSchemaParser<>();

    private Class<?> specificReturnClass;
    private Method specificReturnClassParseMethod;
    private boolean deriveClass;
    private String messageTypeName;
    private boolean readTypeRef = true;
    private boolean readIndexes = false;

    private final Map<String, Method> parseMethodsCache = new ConcurrentHashMap<>();

    /**
     * Cache for derived class names from Descriptor.
     * The mapping from Descriptor to class name is deterministic and can be cached.
     */
    private final Map<String, String> derivedClassNameCache = new ConcurrentHashMap<>();

    public ProtobufDeserializer() {
        super();
    }

    public ProtobufDeserializer(RegistryClientFacade clientFacade, SchemaResolver<ProtobufSchema, U> schemaResolver) {
        super(clientFacade, schemaResolver);
    }

    public ProtobufDeserializer(RegistryClientFacade clientFacade, SchemaResolver<ProtobufSchema, U> schemaResolver,
                                ArtifactReferenceResolverStrategy<ProtobufSchema, U> strategy) {
        super(clientFacade, strategy, schemaResolver);
    }

    public ProtobufDeserializer(RegistryClientFacade clientFacade) {
        super(clientFacade);
    }

    public ProtobufDeserializer(SchemaResolver<ProtobufSchema, U> schemaResolver) {
        super(schemaResolver);
    }

    public void configure(SerdeConfig configs, boolean isKey) {
        ProtobufDeserializerConfig config = new ProtobufDeserializerConfig(configs.originals(), isKey);
        super.configure(config, isKey);

        specificReturnClass = config.getSpecificReturnClass();
        try {
            if (specificReturnClass != null) {
                if (specificReturnClass.equals(DynamicMessage.class)) {
                    this.specificReturnClassParseMethod = specificReturnClass
                            .getDeclaredMethod(PROTOBUF_PARSE_METHOD, Descriptor.class, InputStream.class);
                } else if (!specificReturnClass.equals(Object.class)) {
                    this.specificReturnClassParseMethod = specificReturnClass
                            .getDeclaredMethod(PROTOBUF_PARSE_METHOD, InputStream.class);
                } else {
                    throw new IllegalStateException("Class " + specificReturnClass.getCanonicalName()
                            + " is not a valid protobuf message class");
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Class " + specificReturnClass.getCanonicalName()
                    + " is not a valid protobuf message class", e);
        }

        deriveClass = config.deriveClass();
        readTypeRef = config.readTypeRef();
        readIndexes = config.readIndexes();
    }

    @Override
    public SchemaParser<ProtobufSchema, U> schemaParser() {
        return parser;
    }

    /**
     * @see AbstractDeserializer#readData(io.apicurio.registry.resolver.ParsedSchema, java.nio.ByteBuffer,
     *      int, int)
     */
    @Override
    protected U readData(ParsedSchema<ProtobufSchema> schema, ByteBuffer buffer, int start, int length) {
        return internalReadData(schema, buffer, start, length);
    }

    @SuppressWarnings("unchecked")
    protected U internalReadData(ParsedSchema<ProtobufSchema> schema, ByteBuffer buff, int start,
            int length) {
        try {
            // Create a ByteBuffer slice to avoid copying data
            ByteBuffer slice = buff.duplicate();
            slice.position(start);
            slice.limit(start + length);

            InputStream is = new ByteBufferInputStream(slice);

            // Fast path: if messageTypeName is a Java class name (contains '.'),
            // we can skip Ref parsing and use invokeParseMethod directly.
            // Note: indexes and Ref may still be written to the stream, so we must skip them.
            if (messageTypeName != null && messageTypeName.contains(".")) {
                if (readIndexes) {
                    MessageIndexesUtil.readFrom(is);
                }
                if (readTypeRef) {
                    skipDelimitedMessage(is);
                }
                return invokeParseMethod(is, messageTypeName);
            }

            Descriptor descriptor = null;

            if (messageTypeName != null) {
                descriptor = schema.getParsedSchema().getFileDescriptor()
                        .findMessageTypeByName(messageTypeName);

            }

            if (readIndexes) {
                // Read the message index list from the buffer.  Currently we do not use it for anything,
                // but this may be necessary for interoperability with Confluent.
                MessageIndexesUtil.readFrom(is);
            }

            if (readTypeRef && descriptor == null) {
                try {
                    Ref ref = Ref.parseDelimitedFrom(is);
                    descriptor = schema.getParsedSchema().getFileDescriptor()
                            .findMessageTypeByName(ref.getName());
                } catch (IOException e) {
                    // Reset the stream by creating a new ByteBufferInputStream from a fresh slice
                    slice.position(start);
                    slice.limit(start + length);
                    is = new ByteBufferInputStream(slice);
                    // use the first message type found
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
                    throw new IllegalStateException("Not a valid protobuf builder", e);
                }
            } else if (deriveClass) {
                String className = deriveClassFromDescriptor(descriptor);
                if (className != null) {
                    return invokeParseMethod(is, className);
                }
            } else if (messageTypeName != null) {
                return invokeParseMethod(is, messageTypeName);
            }

            return (U) DynamicMessage.parseFrom(descriptor, is);

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void setMessageTypeName(String messageTypeName) {
        this.messageTypeName = messageTypeName;
    }

    @SuppressWarnings("unchecked")
    public U invokeParseMethod(InputStream buffer, String className) {
        try {
            Method parseMethod = parseMethodsCache.computeIfAbsent(className, k -> {
                Class<?> protobufClass = Utils.loadClass(className);
                try {
                    return protobufClass.getDeclaredMethod(PROTOBUF_PARSE_METHOD, InputStream.class);
                } catch (NoSuchMethodException | SecurityException e) {
                    throw new IllegalStateException(
                            "Class " + className + " is not a valid protobuf message class", e);
                }
            });
            return (U) parseMethod.invoke(null, buffer);
        } catch (IllegalAccessException | InvocationTargetException e) {
            parseMethodsCache.remove(className);
            throw new IllegalStateException("Not a valid protobuf builder", e);
        }
    }

    /**
     * Derives the Java class name from a Protobuf Descriptor.
     * Results are cached since the mapping is deterministic for a given Descriptor.
     */
    public String deriveClassFromDescriptor(Descriptor des) {
        // Use the descriptor's full name as cache key
        String cacheKey = des.getFullName();
        return derivedClassNameCache.computeIfAbsent(cacheKey, key -> computeClassName(des));
    }

    private String computeClassName(Descriptor des) {
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

    /**
     * Skips a length-delimited protobuf message in the stream without parsing it.
     * This is more efficient than parsing and discarding when we don't need the content.
     */
    private void skipDelimitedMessage(InputStream is) throws IOException {
        // Read the varint length prefix
        int length = readRawVarint32(is);
        if (length > 0) {
            // Skip that many bytes
            long skipped = is.skip(length);
            // If skip didn't work (some streams don't support it), read the bytes
            while (skipped < length) {
                int read = is.read();
                if (read < 0) break;
                skipped++;
            }
        }
    }

    /**
     * Reads a varint32 from the input stream (same algorithm as protobuf uses).
     */
    private int readRawVarint32(InputStream is) throws IOException {
        int result = 0;
        int shift = 0;
        while (shift < 32) {
            int b = is.read();
            if (b < 0) {
                return result; // End of stream
            }
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
        }
        // Discard remaining bytes for malformed varints
        while (true) {
            int b = is.read();
            if (b < 0 || (b & 0x80) == 0) {
                return result;
            }
        }
    }

}
