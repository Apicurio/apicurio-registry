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
import io.apicurio.registry.serde.AbstractSerializer;
import io.apicurio.registry.serde.config.SerdeConfig;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An implementation of the Kafka Serializer for JSON Schema use-cases. This serializer assumes that the
 * user's application needs to serialize a Java Bean to JSON data using Jackson. In addition to standard
 * serialization of the bean, this implementation can also optionally validate it against a JSON schema.
 */
public class JsonSchemaSerializer<T> extends AbstractSerializer<JsonSchema, T> {

    private ObjectMapper mapper;
    private final JsonSchemaParser<T> parser = new JsonSchemaParser<>();

    private Boolean validationEnabled;

    public JsonSchemaSerializer() {
        super();
    }

    public JsonSchemaSerializer(RegistryClient client,
            ArtifactReferenceResolverStrategy<JsonSchema, T> artifactResolverStrategy,
            SchemaResolver<JsonSchema, T> schemaResolver) {
        super(client, artifactResolverStrategy, schemaResolver);
    }

    public JsonSchemaSerializer(RegistryClient client) {
        super(client);
    }

    public JsonSchemaSerializer(RegistryClient client, SchemaResolver<JsonSchema, T> schemaResolver) {
        super(client, schemaResolver);
    }

    public JsonSchemaSerializer(SchemaResolver<JsonSchema, T> schemaResolver) {
        super(schemaResolver);
    }

    public JsonSchemaSerializer(RegistryClient client, Boolean validationEnabled) {
        this(client);
        this.validationEnabled = validationEnabled;
    }

    public void configure(SerdeConfig configs, boolean isKey) {
        JsonSchemaSerializerConfig config = new JsonSchemaSerializerConfig(configs.originals());

        if (validationEnabled == null) {
            this.validationEnabled = config.validationEnabled();
        }

        if (null == mapper) {
            this.mapper = new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        }

        super.configure(config, isKey);
    }

    @Override
    public SchemaParser<JsonSchema, T> schemaParser() {
        return parser;
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
     * @see io.apicurio.registry.serde.AbstractSerializer#serializeData(io.apicurio.registry.resolver.ParsedSchema,
     *      java.lang.Object, java.io.OutputStream)
     */
    @Override
    public void serializeData(ParsedSchema<JsonSchema> schema, T data, OutputStream out) throws IOException {
        final byte[] dataBytes = mapper.writeValueAsBytes(data);

        if (isValidationEnabled()) {
            JsonSchemaValidationUtil.validateDataWithSchema(schema, dataBytes, mapper);
        }

        out.write(dataBytes);
    }
}
