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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProtobufSerializer<U extends Message> extends AbstractSerializer<ProtobufSchema, U> {

    private Boolean validationEnabled;
    private ProtobufSchemaParser<U> parser = new ProtobufSchemaParser<>();

    private boolean writeRef = true;
    private boolean writeIndexes = false;

    /**
     * Cache for Ref objects per message type name.
     * Ref objects are immutable and can be safely reused.
     */
    private final Map<String, Ref> refCache = new ConcurrentHashMap<>();

    /**
     * Cache for validation results. Key is composed of schema contentHash + message type name.
     * If validation passed once for a schema+message type combination, it will always pass.
     */
    private final Map<String, Boolean> validationCache = new ConcurrentHashMap<>();

    /**
     * Cache for message indexes per message type full name.
     * The indexes are computed based on the message type path in the schema and don't change.
     */
    private final Map<String, List<Integer>> indexCache = new ConcurrentHashMap<>();

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
            // Create a cache key from schema identity and message type
            String validationKey = getValidationCacheKey(schema, messageTypeName);

            // Check if we've already validated this combination
            Boolean cachedResult = validationCache.get(validationKey);
            if (cachedResult == null) {
                // Perform validation
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

                // Cache successful validation
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
        // Use cached Ref object - Ref is immutable so safe to reuse
        Ref ref = refCache.computeIfAbsent(messageTypeName,
                name -> Ref.newBuilder().setName(name).build());
        ref.writeDelimitedTo(out);
    }

    private String getValidationCacheKey(ParsedSchema<ProtobufSchema> schema, String messageTypeName) {
        // Use the schema's file descriptor name + message type as the cache key
        // This is stable for a given schema version
        String schemaId = schema.getParsedSchema().getFileDescriptor().getFullName();
        return schemaId + ":" + messageTypeName;
    }

    private <U extends Message> void writeIndexes(ParsedSchema<ProtobufSchema> schema, U data, OutputStream out) throws IOException {
        String fullName = data.getDescriptorForType().getFullName();
        // Use cached indexes - they don't change for a given message type
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
        // Schema from protobuf generated class - use FileDescriptor directly
        ProtobufFile fileAfter = new ProtobufFile(data.getDescriptorForType().getFile());

        // Check for differences
        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore,
                fileAfter);
        return checker.findDifferences(false);
    }
}
