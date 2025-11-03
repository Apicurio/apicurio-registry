package io.apicurio.registry.serde.jsonschema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
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

    public JsonSchemaSerializer(RegistryClientFacade clientFacade,
                                ArtifactReferenceResolverStrategy<JsonSchema, T> artifactResolverStrategy,
                                SchemaResolver<JsonSchema, T> schemaResolver) {
        super(clientFacade, artifactResolverStrategy, schemaResolver);
    }

    public JsonSchemaSerializer(RegistryClientFacade clientFacade) {
        super(clientFacade);
    }

    public JsonSchemaSerializer(RegistryClientFacade clientFacade, SchemaResolver<JsonSchema, T> schemaResolver) {
        super(clientFacade, schemaResolver);
    }

    public JsonSchemaSerializer(SchemaResolver<JsonSchema, T> schemaResolver) {
        super(schemaResolver);
    }

    public JsonSchemaSerializer(RegistryClientFacade clientFacade, Boolean validationEnabled) {
        this(clientFacade);
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
     * Serializes the data to JSON format and optionally validates it against the schema.
     * When validation is enabled, the data is converted to JsonNode once and validated
     * before serialization, avoiding redundant parsing.
     *
     * @see io.apicurio.registry.serde.AbstractSerializer#serializeData(io.apicurio.registry.resolver.ParsedSchema,
     *      java.lang.Object, java.io.OutputStream)
     */
    @Override
    public void serializeData(ParsedSchema<JsonSchema> schema, T data, OutputStream out) throws IOException {
        if (isValidationEnabled()) {
            // Convert to JsonNode for validation to avoid serializing and then parsing back
            JsonNode jsonNode = mapper.valueToTree(data);
            JsonSchemaValidationUtil.validateDataWithSchema(schema, jsonNode);
            // Serialize the validated JsonNode
            mapper.writeValue(out, jsonNode);
        } else {
            // When validation is disabled, serialize directly
            mapper.writeValue(out, data);
        }
    }
}
