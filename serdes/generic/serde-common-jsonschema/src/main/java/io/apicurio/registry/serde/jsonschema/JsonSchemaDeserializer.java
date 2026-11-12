package io.apicurio.registry.serde.jsonschema;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.networknt.schema.Schema;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.resolver.utils.Utils;
import io.apicurio.registry.serde.AbstractDeserializer;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.utils.ByteBufferInputStream;
import tools.jackson.core.JacksonException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

public class JsonSchemaDeserializer<T> extends AbstractDeserializer<Schema, T> {

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

    public JsonSchemaDeserializer(RegistryClientFacade clientFacade, SchemaResolver<Schema, T> schemaResolver) {
        super(clientFacade, schemaResolver);
    }

    public JsonSchemaDeserializer(RegistryClientFacade clientFacade) {
        super(clientFacade);
    }

    public JsonSchemaDeserializer(SchemaResolver<Schema, T> schemaResolver) {
        super(schemaResolver);
    }

    public JsonSchemaDeserializer(RegistryClientFacade clientFacade, SchemaResolver<Schema, T> schemaResolver,
                                  ArtifactReferenceResolverStrategy<Schema, T> strategy) {
        super(clientFacade, strategy, schemaResolver);
    }

    public JsonSchemaDeserializer(RegistryClientFacade clientFacade, Boolean validationEnabled) {
        this(clientFacade);
        this.validationEnabled = validationEnabled;
    }

    public void configure(SerdeConfig configs, boolean isKey) {
        JsonSchemaDeserializerConfig config = new JsonSchemaDeserializerConfig(configs.originals(), isKey);
        super.configure(config, isKey);

        if (validationEnabled == null) {
            this.validationEnabled = config.validationEnabled();
        }

        this.specificReturnClass = (Class<T>) config.getSpecificReturnClass();

        if (null == mapper) {
            mapper = JsonMapper.builder()
                    .changeDefaultPropertyInclusion(incl -> incl.withContentInclusion(JsonInclude.Include.NON_NULL).withValueInclusion(JsonInclude.Include.NON_NULL))
                    .build();
            ;
        }
    }

    @Override
    public SchemaParser<Schema, T> schemaParser() {
        return parser;
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
     * @see AbstractDeserializer#readData(io.apicurio.registry.resolver.ParsedSchema, java.nio.ByteBuffer,
     *      int, int)
     */
    @Override
    public T readData(ParsedSchema<Schema> schema, ByteBuffer buffer, int start, int length) {
        return internalReadData(schema, buffer, start, length);
    }

    private T internalReadData(ParsedSchema<Schema> schema, ByteBuffer buffer, int start, int length) {
        try {
            // Figure out the Class to use (the message type) for deserialization.  This may end up
            // being null, in which case a Jackson Node will be returned.
            Class<T> messageType;
            if (this.specificReturnClass != null) {
                messageType = this.specificReturnClass;
            } else {
                String javaType = null;
                JsonNode javaTypeNode = schema.getParsedSchema().getSchemaNode().get("javaType");
                if (javaTypeNode != null && !javaTypeNode.isNull()) {
                    javaType = javaTypeNode.stringValue();
                }
                // TODO if javaType is null, maybe warn something like this?
                // You can try configure the property \"apicurio.registry.serde.json-schema.java-type\" with
                // the full class name to use for deserialization
                messageType = javaType == null ? null : Utils.loadClass(javaType);
            }

            // Parse the data into a Node once
            ByteBuffer slice = buffer.duplicate();
            slice.position(start);
            slice.limit(start + length);
            JsonNode jsonNode = mapper.readTree(new ByteBufferInputStream(slice));

            // Validate the data (if enabled)
            if (isValidationEnabled()) {
                JsonSchemaValidationUtil.validateDataWithSchema(schema, jsonNode);
            }

            // Convert to specific Java class (messageType) if we have one configured
            if (messageType == null) {
                // TODO maybe warn there is no message type and the deserializer will return a JsonNode
                return (T) jsonNode;
            } else {
                return mapper.treeToValue(jsonNode, messageType);
            }
        } catch (JacksonException e) {
            // Jackson 3 exceptions are unchecked (unlike Jackson 2's JsonProcessingException)
            throw new UncheckedIOException(new IOException(e.getMessage(), e));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
