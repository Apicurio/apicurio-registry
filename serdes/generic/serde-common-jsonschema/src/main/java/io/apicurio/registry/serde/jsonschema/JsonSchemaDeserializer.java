package io.apicurio.registry.serde.jsonschema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.resolver.utils.Utils;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.AbstractDeserializer;
import io.apicurio.registry.serde.config.SerdeConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

public class JsonSchemaDeserializer<T> extends AbstractDeserializer<JsonSchema, T> {

    private ObjectMapper mapper;
    private Boolean validationEnabled;
    private final JsonSchemaParser<T> parser = new JsonSchemaParser<>();

    /**
     * Optional, the full class name of the java class to deserialize
     */
    private Class<T> specificReturnClass;

    public JsonSchemaDeserializer() {
        super();
    }

    public JsonSchemaDeserializer(RegistryClient client, SchemaResolver<JsonSchema, T> schemaResolver) {
        super(client, schemaResolver);
    }

    public JsonSchemaDeserializer(RegistryClient client) {
        super(client);
    }

    public JsonSchemaDeserializer(SchemaResolver<JsonSchema, T> schemaResolver) {
        super(schemaResolver);
    }

    public JsonSchemaDeserializer(RegistryClient client, SchemaResolver<JsonSchema, T> schemaResolver,
            ArtifactReferenceResolverStrategy<JsonSchema, T> strategy) {
        super(client, strategy, schemaResolver);
    }

    public JsonSchemaDeserializer(RegistryClient client, Boolean validationEnabled) {
        this(client);
        this.validationEnabled = validationEnabled;
    }

    /**
     * @see AbstractDeserializer#configure(SerdeConfig, boolean)
     */
    @SuppressWarnings("unchecked")
    @Override
    public void configure(SerdeConfig configs, boolean isKey) {
        JsonSchemaDeserializerConfig config = new JsonSchemaDeserializerConfig(configs.originals(), isKey);
        super.configure(config, isKey);

        if (validationEnabled == null) {
            this.validationEnabled = config.validationEnabled();
        }

        this.specificReturnClass = (Class<T>) config.getSpecificReturnClass();

        if (null == mapper) {
            mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL);
            ;
        }
    }

    public boolean isValidationEnabled() {
        return validationEnabled != null && validationEnabled;
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.mapper = objectMapper;
    }

    public void setSpecificReturnClass(Class<T> specificReturnClass) {
        this.specificReturnClass = specificReturnClass;
    }

    public Class<T> getSpecificReturnClass() {
        return specificReturnClass;
    }

    /**
     * @see io.apicurio.registry.serde.AbstractSerDe#schemaParser()
     */
    @Override
    public SchemaParser<JsonSchema, T> schemaParser() {
        return parser;
    }

    /**
     * @see AbstractDeserializer#readData(io.apicurio.registry.resolver.ParsedSchema, java.nio.ByteBuffer,
     *      int, int)
     */
    @Override
    public T readData(ParsedSchema<JsonSchema> schema, ByteBuffer buffer, int start, int length) {
        return internalReadData(schema, buffer, start, length);
    }

    private T internalReadData(ParsedSchema<JsonSchema> schema, ByteBuffer buffer, int start, int length) {
        byte[] data = new byte[length];
        System.arraycopy(buffer.array(), start, data, 0, length);

        try {
            JsonParser parser = mapper.getFactory().createParser(data);

            if (isValidationEnabled()) {
                JsonSchemaValidationUtil.validateDataWithSchema(schema, data, mapper);
            }

            Class<T> messageType = null;

            if (this.specificReturnClass != null) {
                messageType = this.specificReturnClass;
            } else {
                JsonNode jsonSchema = mapper.readTree(schema.getRawSchema());

                String javaType = null;
                JsonNode javaTypeNode = jsonSchema.get("javaType");
                if (javaTypeNode != null && !javaTypeNode.isNull()) {
                    javaType = javaTypeNode.textValue();
                }
                // TODO if javaType is null, maybe warn something like this?
                // You can try configure the property \"apicurio.registry.serde.json-schema.java-type\" with
                // the full class name to use for deserialization
                messageType = javaType == null ? null : Utils.loadClass(javaType);
            }

            if (messageType == null) {
                // TODO maybe warn there is no message type and the deserializer will return a JsonNode
                return mapper.readTree(parser);
            } else {
                return mapper.readValue(parser, messageType);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
