package io.apicurio.registry.utils.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.ParsedSchemaImpl;
import io.apicurio.registry.resolver.SchemaLookupResult;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.SchemaResolverConfigurer;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.converter.json.FormatStrategy;
import io.apicurio.registry.utils.converter.json.JsonConverterMetadata;
import io.apicurio.registry.utils.converter.json.JsonConverterRecord;
import io.apicurio.registry.utils.converter.json.PrettyFormatStrategy;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.json.JsonConverter;
import org.apache.kafka.connect.json.JsonConverterConfig;
import org.apache.kafka.connect.json.JsonDeserializer;
import org.apache.kafka.connect.storage.Converter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ExtJsonConverter extends SchemaResolverConfigurer<JsonNode, Object>
        implements Converter, SchemaParser<JsonNode, Object>, AutoCloseable {
    private final JsonConverter jsonConverter;
    private final JsonConverter deserializingConverter;
    private final ObjectMapper mapper;
    private FormatStrategy formatStrategy;
    private boolean isKey;

    private JsonDeserializer jsonDeserializer;

    public ExtJsonConverter() {
        this(null);
    }

    public ExtJsonConverter(RegistryClient client) {
        super(client);
        this.jsonConverter = new JsonConverter();
        this.deserializingConverter = new JsonConverter();
        this.mapper = new ObjectMapper();
        this.formatStrategy = new PrettyFormatStrategy();
        this.jsonDeserializer = new JsonDeserializer();
    }

    public ExtJsonConverter setFormatStrategy(FormatStrategy formatStrategy) {
        this.formatStrategy = Objects.requireNonNull(formatStrategy);
        return this;
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure((Map<String, Object>) configs, isKey, this);
        this.isKey = isKey;
        Map<String, Object> wrapper = new HashMap<>(configs);
        wrapper.put(JsonConverterConfig.SCHEMAS_ENABLE_CONFIG, false);
        jsonConverter.configure(wrapper, isKey);

        Map<String, Object> deserializingConfig = new HashMap<>(configs);
        wrapper.put(JsonConverterConfig.SCHEMAS_ENABLE_CONFIG, true);
        deserializingConverter.configure(deserializingConfig, false);
        jsonDeserializer.configure(wrapper, false);
    }

    @Override
    public byte[] fromConnectData(String topic, Schema schema, Object value) {
        return fromConnectData(topic, null, schema, value);
    }

    @Override
    public byte[] fromConnectData(String topic, Headers headers, Schema schema, Object value) {
        if (schema == null && value == null) {
            return null;
        }

        JsonConverterRecord<Object> record = new JsonConverterRecord<Object>(
                new JsonConverterMetadata(topic, isKey, headers, schema), value);
        SchemaLookupResult<JsonNode> schemaLookupResult = getSchemaResolver().resolveSchema(record);

        byte[] payload = jsonConverter.fromConnectData(topic, schema, value);

        return formatStrategy.fromConnectData(schemaLookupResult.getContentId(), payload);

    }

    @Override
    public SchemaAndValue toConnectData(String topic, byte[] value) {
        FormatStrategy.IdPayload ip = formatStrategy.toConnectData(value);

        long contentId = ip.getContentId();

        SchemaLookupResult<JsonNode> schemaLookupResult = getSchemaResolver()
                .resolveSchemaByArtifactReference(ArtifactReference.builder().contentId(contentId).build());

        JsonNode parsedSchema = schemaLookupResult.getParsedSchema().getParsedSchema();
        JsonNode dataDeserialized = jsonDeserializer.deserialize(topic, ip.getPayload());

        // Since the json converter is expecting the data to have the schema to fully validate it, we build an
        // envelope object containing the schema from registry and the data deserialized
        ObjectNode envelope = JsonNodeFactory.withExactBigDecimals(true).objectNode();
        envelope.set("schema", parsedSchema);
        envelope.set("payload", dataDeserialized);
        dataDeserialized = envelope;

        SchemaAndValue sav;
        try {
            sav = deserializingConverter.toConnectData(topic, mapper.writeValueAsBytes(dataDeserialized));

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        Schema schema = deserializingConverter
                .asConnectSchema(schemaLookupResult.getParsedSchema().getParsedSchema());

        return new SchemaAndValue(schema, sav.value());
    }

    /**
     * @see io.apicurio.registry.serde.SchemaParser#artifactType()
     */
    @Override
    public String artifactType() {
        return ArtifactType.KCONNECT;
    }

    /**
     * @see io.apicurio.registry.serde.SchemaParser#parseSchema(byte[])
     */
    @Override
    public JsonNode parseSchema(byte[] rawSchema, Map<String, ParsedSchema<JsonNode>> resolvedReferences) {
        try {
            return mapper.readTree(rawSchema);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @see io.apicurio.registry.resolver.SchemaParser#getSchemaFromData(io.apicurio.registry.resolver.data.Record)
     */
    @Override
    public ParsedSchema<JsonNode> getSchemaFromData(Record<Object> data) {
        JsonConverterRecord<Object> jcr = (JsonConverterRecord<Object>) data;
        JsonNode jsonSchema = jsonConverter.asJsonSchema(jcr.metadata().getSchema());
        String schemaString = jsonSchema != null ? jsonSchema.toString() : null;
        return new ParsedSchemaImpl<JsonNode>().setParsedSchema(jsonSchema)
                .setRawSchema(IoUtil.toBytes(schemaString));
    }

    @Override
    public ParsedSchema<JsonNode> getSchemaFromData(Record<Object> data, boolean dereference) {
        return getSchemaFromData(data);
    }

    /**
     * @see java.lang.AutoCloseable#close()
     */
    @Override
    public void close() throws Exception {
        jsonConverter.close();
    }
}
