package io.apicurio.registry.serde.protobuf;

import com.google.protobuf.Message;
import io.apicurio.registry.protobuf.ProtobufDifference;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.rules.compatibility.protobuf.ProtobufCompatibilityCheckerLibrary;
import io.apicurio.registry.serde.AbstractSerializer;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.protobuf.ref.RefOuterClass.Ref;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchema;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProtobufSerializer<U extends Message> extends AbstractSerializer<ProtobufSchema, U> {

    /**
     * Maximum number of entries in each cache.
     * Prevents unbounded memory growth in long-running applications.
     */
    private static final int MAX_CACHE_SIZE = 1000;

    private Boolean validationEnabled;
    private ProtobufSchemaParser<U> parser = new ProtobufSchemaParser<>();

    private boolean writeRef = true;
    private boolean writeIndexes = false;

    /**
     * Cache for Ref objects per message type name.
     * Ref objects are immutable and can be safely reused.
     * Uses LRU eviction to prevent unbounded growth.
     */
    private final Map<String, Ref> refCache = createBoundedCache(MAX_CACHE_SIZE);

    /**
     * Cache for validation results. Key is composed of schema contentHash + message type name.
     * If validation passed once for a schema+message type combination, it will always pass.
     * Uses LRU eviction to prevent unbounded growth.
     */
    private final Map<String, Boolean> validationCache = createBoundedCache(MAX_CACHE_SIZE);

    /**
     * Cache for message indexes per message type full name.
     * The indexes are computed based on the message type path in the schema and don't change.
     * Uses LRU eviction to prevent unbounded growth.
     */
    private final Map<String, List<Integer>> indexCache = createBoundedCache(MAX_CACHE_SIZE);

    /**
     * Creates a thread-safe bounded LRU cache.
     * When the cache exceeds maxSize, the least recently accessed entry is removed.
     */
    private static <K, V> Map<K, V> createBoundedCache(int maxSize) {
        return Collections.synchronizedMap(new LinkedHashMap<K, V>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxSize;
            }
        });
    }

    public ProtobufSerializer() {
        super();
    }

    public ProtobufSerializer(RegistryClientFacade clientFacade,
                              ArtifactReferenceResolverStrategy<ProtobufSchema, U> artifactResolverStrategy,
                              SchemaResolver<ProtobufSchema, U> schemaResolver) {
        super(clientFacade, artifactResolverStrategy, schemaResolver);
    }

    public ProtobufSerializer(RegistryClientFacade clientFacade) {
        super(clientFacade);
    }

    public ProtobufSerializer(SchemaResolver<ProtobufSchema, U> schemaResolver) {
        super(schemaResolver);
    }

    public ProtobufSerializer(RegistryClientFacade clientFacade, SchemaResolver<ProtobufSchema, U> schemaResolver) {
        super(clientFacade, schemaResolver);
    }

    public ProtobufSerializer(RegistryClientFacade clientFacade, SchemaResolver<ProtobufSchema, U> schemaResolver,
                              ArtifactReferenceResolverStrategy<ProtobufSchema, U> strategy) {
        super(clientFacade, strategy, schemaResolver);
    }

    @Override
    public void configure(SerdeConfig configs, boolean isKey) {
        ProtobufSerializerConfig config = new ProtobufSerializerConfig(configs.originals());
        super.configure(config, isKey);

        validationEnabled = config.validationEnabled();
        writeRef = config.sendTypeRef();
        writeIndexes = config.sendIndexes();
    }

    /**
     * @see AbstractSerializer#schemaParser()
     */
    @Override
    public SchemaParser<ProtobufSchema, U> schemaParser() {
        return parser;
    }

    /**
     * @see io.apicurio.registry.serde.AbstractSerializer#serializeData(io.apicurio.registry.resolver.ParsedSchema,
     *      java.lang.Object, java.io.OutputStream)
     */
    @Override
    public void serializeData(ParsedSchema<ProtobufSchema> schema, U data, OutputStream out)
            throws IOException {
        String messageTypeName = data.getDescriptorForType().getName();

        if (validationEnabled) {
            // Check validation cache first
            String validationKey = schema.getRawSchema().hashCode() + ":" + messageTypeName;
            Boolean cachedValidation = validationCache.get(validationKey);

            if (cachedValidation == null) {
                if (schema.getParsedSchema() != null && schema.getParsedSchema().getFileDescriptor()
                        .findMessageTypeByName(messageTypeName) == null) {
                    throw new IllegalStateException("Missing message type "
                            + messageTypeName + " in the protobuf schema");
                }

                List<ProtobufDifference> diffs = validate(schema, data);
                if (!diffs.isEmpty()) {
                    throw new IllegalStateException(
                            "The data to send is not compatible with the schema. " + diffs);
                }
                validationCache.put(validationKey, Boolean.TRUE);
            }
        }
        if (writeRef) {
            writeRef(messageTypeName, out);
        }
        if (writeIndexes) {
            writeIndexes(schema, data, out);
        }

        data.writeTo(out);
    }

    private void writeRef(String messageTypeName, OutputStream out) throws IOException {
        Ref ref = refCache.computeIfAbsent(messageTypeName,
                name -> Ref.newBuilder().setName(name).build());
        ref.writeDelimitedTo(out);
    }

    private void writeIndexes(ParsedSchema<ProtobufSchema> schema, U data, OutputStream out) throws IOException {
        String fullName = data.getDescriptorForType().getFullName();
        List<Integer> indexes = indexCache.computeIfAbsent(fullName,
                name -> MessageIndexesUtil.getMessageIndexes(schema, data));
        MessageIndexesUtil.writeTo(indexes, out);
    }

    public void setWriteRef(boolean writeRef) {
        this.writeRef = writeRef;
    }

    private List<ProtobufDifference> validate(ParsedSchema<ProtobufSchema> schemaFromRegistry, U data) {
        // Schema from the registry
        ProtobufFile fileBefore = schemaFromRegistry.getParsedSchema().getProtobufFile();
        // Schema from protobuf generated class
        ProtobufFile fileAfter = new ProtobufFile(
                parser.toProtoFileElement(data.getDescriptorForType().getFile()));

        // Check for differences
        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore,
                fileAfter);
        return checker.findDifferences(false);
    }
}
