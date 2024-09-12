package io.apicurio.registry.serde.jsonschema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private ObjectMapper mapper;
    private final JsonSchemaParser<T> parser = new JsonSchemaParser<>();

    private Boolean validationEnabled;
    private MessageTypeSerdeHeaders serdeHeaders;

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

    public JsonSchemaKafkaSerializer(RegistryClient client, Boolean validationEnabled) {
        this(client);
        this.validationEnabled = validationEnabled;
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerializer#configure(java.util.Map, boolean)
     */
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        JsonSchemaKafkaSerializerConfig config = new JsonSchemaKafkaSerializerConfig(configs);
        super.configure(config, isKey);

        if (validationEnabled == null) {
            this.validationEnabled = config.validationEnabled();
        }

        serdeHeaders = new MessageTypeSerdeHeaders(new HashMap<>(configs), isKey);

        if (null == mapper) {
            this.mapper = new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        }
    }

    public boolean isValidationEnabled() {
        return validationEnabled != null && validationEnabled;
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.mapper = objectMapper;
    }

    /**
     * @param validationEnabled the validationEnabled to set
     */
    public void setValidationEnabled(Boolean validationEnabled) {
        this.validationEnabled = validationEnabled;
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerDe#schemaParser()
     */
    @Override
    public SchemaParser<JsonSchema, T> schemaParser() {
        return parser;
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerializer#serializeData(io.apicurio.registry.serde.ParsedSchema,
     *      java.lang.Object, java.io.OutputStream)
     */
    @Override
    protected void serializeData(ParsedSchema<JsonSchema> schema, T data, OutputStream out)
            throws IOException {
        serializeData(null, schema, data, out);
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerializer#serializeData(org.apache.kafka.common.header.Headers,
     *      io.apicurio.registry.serde.ParsedSchema, java.lang.Object, java.io.OutputStream)
     */
    @Override
    protected void serializeData(Headers headers, ParsedSchema<JsonSchema> schema, T data, OutputStream out)
            throws IOException {
        final byte[] dataBytes = mapper.writeValueAsBytes(data);
        if (isValidationEnabled()) {
            JsonSchemaValidationUtil.validateDataWithSchema(schema, dataBytes, mapper);
        }
        if (headers != null) {
            serdeHeaders.addMessageTypeHeader(headers, data.getClass().getName());
        }
        out.write(dataBytes);
    }
}
