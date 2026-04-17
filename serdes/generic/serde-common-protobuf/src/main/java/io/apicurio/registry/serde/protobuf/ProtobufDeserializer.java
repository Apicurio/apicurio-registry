package io.apicurio.registry.serde.protobuf;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import com.microsoft.kiota.ApiException;
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

import java.io.ByteArrayInputStream;
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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProtobufDeserializer.class);

    private Class<?> specificReturnClass;
    private Method specificReturnClassParseMethod;
    private boolean deriveClass;
    private boolean fallbackOnSchemaError;
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
        fallbackOnSchemaError = config.fallbackOnSchemaError();
    }

    /**
     * Overrides the default deserialization to add a fallback path when schema resolution fails
     * for a recoverable reason and both {@code specificReturnClass} and
     * {@code fallbackOnSchemaError} are configured.
     * <p>
     * The fallback strips the Apicurio wire-format prefix and parses the raw protobuf bytes
     * directly using the configured return class. It is only triggered for the specific
     * unchecked exception types that represent transient or recoverable failures of the
     * schema lookup, caught by type rather than by inspecting causes:
     * </p>
     * <ul>
     *   <li>{@link com.microsoft.kiota.ApiException} - registry returned an HTTP error
     *       (404 schema not yet propagated, 5xx registry outage, etc.)</li>
     *   <li>{@link UncheckedIOException} - network or I/O failure reading from the registry
     *       (connection refused, timeout, broken stream); the JDK and {@code IoUtil} wrap
     *       any underlying {@link java.io.IOException} in this type</li>
     *   <li>{@link IllegalStateException} <em>only</em> when its direct cause is a
     *       {@link DescriptorValidationException}; this is how {@link ProtobufSchemaParser}
     *       surfaces a registry-supplied descriptor with missing transitive imports.
     *       {@code IllegalStateException} from any other source (configuration errors,
     *       invalid state in the resolver, etc.) is rethrown unchanged</li>
     * </ul>
     * <p>
     * Programming errors and configuration mistakes ({@link NullPointerException},
     * {@link IllegalArgumentException}, {@link ClassCastException}, generic
     * {@link RuntimeException}, etc.) are <strong>not</strong> caught and propagate to the
     * caller so real bugs are not silently masked.
     * </p>
     * <p>
     * Typical scenarios where the fallback rescues a deserialization:
     * </p>
     * <ul>
     *   <li>Registry downtime or transient network failures</li>
     *   <li>Missing transitive proto imports in the registry</li>
     *   <li>Race conditions during rolling deployments (consumer starts before producer
     *       registers schema)</li>
     * </ul>
     */
    @Override
    public U deserializeData(String topic, byte[] data) {
        try {
            return super.deserializeData(topic, data);
        } catch (ApiException | UncheckedIOException e) {
            return tryFallback(topic, data, e);
        } catch (IllegalStateException e) {
            if (e.getCause() instanceof DescriptorValidationException) {
                return tryFallback(topic, data, e);
            }
            throw e;
        }
    }

    /**
     * Invokes the direct-parse fallback when it is enabled and a {@code specificReturnClass}
     * is configured; otherwise rethrows the original error so the caller observes the
     * unmodified failure.
     *
     * @param topic         the topic the message belongs to (for logging only)
     * @param data          the raw wire-format bytes to fall back on
     * @param originalError the recoverable schema-resolution failure that triggered fallback
     * @return the parsed message produced by the fallback
     */
    @SuppressWarnings("unchecked")
    private U tryFallback(String topic, byte[] data, RuntimeException originalError) {
        if (!fallbackEnabled()) {
            throw originalError;
        }
        log.warn("Schema resolution failed for topic '{}' ({}). "
                + "Falling back to direct protobuf parsing with {}.",
                topic, rootCauseMessage(originalError), specificReturnClass.getName());
        try {
            return (U) parseFallback(data);
        } catch (RuntimeException fallbackEx) {
            fallbackEx.addSuppressed(originalError);
            throw fallbackEx;
        }
    }

    private boolean fallbackEnabled() {
        return fallbackOnSchemaError
                && specificReturnClassParseMethod != null
                && !specificReturnClass.equals(DynamicMessage.class);
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

    /**
     * Parses protobuf data directly using {@link #specificReturnClassParseMethod}, skipping
     * the Apicurio wire-format prefix. Used as a fallback when schema resolution fails.
     * <p>
     * The wire format depends on the configured {@link io.apicurio.registry.serde.IdHandler}:
     * <pre>
     * Non-headers mode: [0x00 magic][ID bytes (size from IdHandler)][indexes?][Ref?][protobuf payload]
     * Headers mode:     [indexes?][Ref?][protobuf payload]
     * </pre>
     * The method respects the {@code readIndexes} and {@code readTypeRef} configuration flags
     * to correctly skip any prefix data before the actual protobuf payload.
     * </p>
     *
     * @param data the raw Kafka message value bytes including wire-format prefix
     * @return the deserialized protobuf message, or {@code null} if data is null
     * @throws IllegalStateException if the fallback parsing fails
     */
    @SuppressWarnings("unchecked")
    private U parseFallback(byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);

            // Skip the Apicurio wire-format prefix (magic byte + ID) if present.
            // The ID size is determined by the configured IdHandler (typically 4 bytes).
            if (data.length > 0 && data[0] == 0x00) {
                bais.skip(1); // magic byte
                int idSize = getSerdeConfigurer().getIdHandler().idSize();
                bais.skip(idSize);
            }

            // Skip message indexes if the producer wrote them (Confluent interop)
            if (readIndexes) {
                MessageIndexesUtil.readFrom(bais);
            }

            // Skip the Ref length-delimited message if the producer wrote a type reference
            if (readTypeRef) {
                skipDelimitedMessage(bais);
            }

            return (U) specificReturnClassParseMethod.invoke(null, bais);
        } catch (IllegalAccessException | InvocationTargetException | IOException e) {
            throw new IllegalStateException("Fallback protobuf parsing failed for "
                    + specificReturnClass.getName(), e);
        }
    }

    /**
     * Returns the message from the deepest cause in the exception chain.
     * Falls back to the exception class name if the message is {@code null}.
     *
     * @param e the throwable to inspect
     * @return a human-readable description of the root cause
     */
    private static String rootCauseMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String msg = cause.getMessage();
        return msg != null ? msg : cause.getClass().getName();
    }
}
