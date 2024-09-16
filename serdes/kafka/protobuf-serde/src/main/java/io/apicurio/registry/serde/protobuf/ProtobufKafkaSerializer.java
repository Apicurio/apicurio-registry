package io.apicurio.registry.serde.protobuf;

import com.google.protobuf.Message;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.AbstractKafkaSerializer;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchema;
import org.apache.kafka.common.header.Headers;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class ProtobufKafkaSerializer<U extends Message> extends AbstractKafkaSerializer<ProtobufSchema, U> {

    private ProtobufSerdeHeaders serdeHeaders;
    private ProtobufSerializer<U> protobufSerializer;

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
        ProtobufSerializerConfig config = new ProtobufSerializerConfig(configs);
        serdeHeaders = new ProtobufSerdeHeaders(new HashMap<>(configs), isKey);
        this.protobufSerializer = new ProtobufSerializer<>();
        if (getSchemaResolver() != null) {
            this.protobufSerializer.setSchemaResolver(getSchemaResolver());
        }
        protobufSerializer.configure(config, isKey);

        super.configure(config, isKey);
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerDe#schemaParser()
     */
    @Override
    public SchemaParser<ProtobufSchema, U> schemaParser() {
        return protobufSerializer.schemaParser();
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerializer#serializeData(io.apicurio.registry.resolver.ParsedSchema,
     *      java.lang.Object, java.io.OutputStream)
     */
    @Override
    protected void serializeData(ParsedSchema<ProtobufSchema> schema, U data, OutputStream out)
            throws IOException {
        protobufSerializer.serializeData(schema, data, out);
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerializer#serializeData(org.apache.kafka.common.header.Headers,
     *      io.apicurio.registry.resolver.ParsedSchema, java.lang.Object, java.io.OutputStream)
     */
    @Override
    protected void serializeData(Headers headers, ParsedSchema<ProtobufSchema> schema, U data,
            OutputStream out) throws IOException {
        if (headers != null) {
            serdeHeaders.addMessageTypeHeader(headers, data.getClass().getName());
            serdeHeaders.addProtobufTypeNameHeader(headers, data.getDescriptorForType().getName());
        } else {
            protobufSerializer.setWriteRef(false);
        }

        serializeData(schema, data, out);
    }
}
