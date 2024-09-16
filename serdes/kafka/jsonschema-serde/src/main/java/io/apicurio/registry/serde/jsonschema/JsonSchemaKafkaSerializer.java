package io.apicurio.registry.serde.jsonschema;

import com.networknt.schema.JsonSchema;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.AbstractKafkaSerializer;
import io.apicurio.registry.serde.headers.MessageTypeSerdeHeaders;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * An implementation of the Kafka Serializer for JSON Schema use-cases. This serializer assumes that the
 * user's application needs to serialize a Java Bean to JSON data using Jackson. In addition to standard
 * serialization of the bean, this implementation can also optionally validate it against a JSON schema.
 */
public class JsonSchemaKafkaSerializer<T> extends AbstractKafkaSerializer<JsonSchema, T>
        implements Serializer<T> {

    private MessageTypeSerdeHeaders serdeHeaders;

    private JsonSchemaSerializer<T> jsonSchemaSerializer;

    public JsonSchemaKafkaSerializer() {
        super();
    }

    public JsonSchemaKafkaSerializer(RegistryClient client,
            ArtifactReferenceResolverStrategy<JsonSchema, T> artifactResolverStrategy,
            SchemaResolver<JsonSchema, T> schemaResolver) {
        super(client, artifactResolverStrategy, schemaResolver);
    }

    public JsonSchemaKafkaSerializer(RegistryClient client) {
        super(client);
    }

    public JsonSchemaKafkaSerializer(SchemaResolver<JsonSchema, T> schemaResolver) {
        super(schemaResolver);
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerializer#configure(java.util.Map, boolean)
     */
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        JsonSchemaSerializerConfig config = new JsonSchemaSerializerConfig(configs);
        this.jsonSchemaSerializer = new JsonSchemaSerializer<>();
        if (getSchemaResolver() != null) {
            this.jsonSchemaSerializer.setSchemaResolver(getSchemaResolver());
        }
        jsonSchemaSerializer.configure(config, isKey);
        serdeHeaders = new MessageTypeSerdeHeaders(new HashMap<>(configs), isKey);

        super.configure(config, isKey);
    }

    /**
     * @param validationEnabled the validationEnabled to set
     */
    public void setValidationEnabled(Boolean validationEnabled) {
        this.jsonSchemaSerializer.setValidationEnabled(validationEnabled);
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerDe#schemaParser()
     */
    @Override
    public SchemaParser<JsonSchema, T> schemaParser() {
        return jsonSchemaSerializer.schemaParser();
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerializer#serializeData(io.apicurio.registry.resolver.ParsedSchema,
     *      java.lang.Object, java.io.OutputStream)
     */
    @Override
    protected void serializeData(ParsedSchema<JsonSchema> schema, T data, OutputStream out)
            throws IOException {
        serializeData(null, schema, data, out);
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerializer#serializeData(org.apache.kafka.common.header.Headers,
     *      io.apicurio.registry.resolver.ParsedSchema, java.lang.Object, java.io.OutputStream)
     */
    @Override
    protected void serializeData(Headers headers, ParsedSchema<JsonSchema> schema, T data, OutputStream out)
            throws IOException {

        if (headers != null) {
            serdeHeaders.addMessageTypeHeader(headers, data.getClass().getName());
        }

        serializeData(schema, data, out);
    }
}
