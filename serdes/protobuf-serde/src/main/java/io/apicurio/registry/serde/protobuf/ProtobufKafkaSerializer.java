package io.apicurio.registry.serde.protobuf;

import com.google.protobuf.Message;
import io.apicurio.registry.protobuf.ProtobufDifference;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rules.compatibility.protobuf.ProtobufCompatibilityCheckerLibrary;
import io.apicurio.registry.serde.AbstractKafkaSerializer;
import io.apicurio.registry.serde.protobuf.ref.RefOuterClass.Ref;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchema;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Headers;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProtobufKafkaSerializer<U extends Message> extends AbstractKafkaSerializer<ProtobufSchema, U> {

    private Boolean validationEnabled;

    private ProtobufSerdeHeaders serdeHeaders;
    private ProtobufSchemaParser<U> parser = new ProtobufSchemaParser<>();

    public ProtobufKafkaSerializer() {
        super();
    }

    public ProtobufKafkaSerializer(RegistryClient client,
            ArtifactReferenceResolverStrategy<ProtobufSchema, U> artifactResolverStrategy,
            SchemaResolver<ProtobufSchema, U> schemaResolver) {
        super(client, artifactResolverStrategy, schemaResolver);
    }

    public ProtobufKafkaSerializer(RegistryClient client) {
        super(client);
    }

    public ProtobufKafkaSerializer(SchemaResolver<ProtobufSchema, U> schemaResolver) {
        super(schemaResolver);
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        ProtobufKafkaSerializerConfig config = new ProtobufKafkaSerializerConfig(configs);
        super.configure(config, isKey);

        serdeHeaders = new ProtobufSerdeHeaders(new HashMap<>(configs), isKey);

        validationEnabled = config.validationEnabled();
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerDe#schemaParser()
     */
    @Override
    public SchemaParser<ProtobufSchema, U> schemaParser() {
        return parser;
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerializer#serializeData(io.apicurio.registry.serde.ParsedSchema,
     *      java.lang.Object, java.io.OutputStream)
     */
    @Override
    protected void serializeData(ParsedSchema<ProtobufSchema> schema, U data, OutputStream out)
            throws IOException {
        serializeData(null, schema, data, out);
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerializer#serializeData(org.apache.kafka.common.header.Headers,
     *      io.apicurio.registry.serde.ParsedSchema, java.lang.Object, java.io.OutputStream)
     */
    @Override
    protected void serializeData(Headers headers, ParsedSchema<ProtobufSchema> schema, U data,
            OutputStream out) throws IOException {
        if (validationEnabled) {

            if (schema.getParsedSchema() != null && schema.getParsedSchema().getFileDescriptor()
                    .findMessageTypeByName(data.getDescriptorForType().getName()) == null) {
                throw new SerializationException("Missing message type "
                        + data.getDescriptorForType().getName() + " in the protobuf schema");
            }

            List<ProtobufDifference> diffs = validate(schema, data);
            if (!diffs.isEmpty()) {
                throw new SerializationException(
                        "The data to send is not compatible with the schema. " + diffs);
            }

        }

        if (headers != null) {
            serdeHeaders.addMessageTypeHeader(headers, data.getClass().getName());
            serdeHeaders.addProtobufTypeNameHeader(headers, data.getDescriptorForType().getName());
        } else {
            Ref ref = Ref.newBuilder().setName(data.getDescriptorForType().getName()).build();
            ref.writeDelimitedTo(out);
        }

        data.writeTo(out);
    }

    private List<ProtobufDifference> validate(ParsedSchema<ProtobufSchema> schemaFromRegistry, U data) {
        ProtobufFile fileBefore = schemaFromRegistry.getParsedSchema().getProtobufFile();
        ProtobufFile fileAfter = new ProtobufFile(
                parser.toProtoFileElement(data.getDescriptorForType().getFile()));
        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore,
                fileAfter);
        return checker.findDifferences();
    }

}
