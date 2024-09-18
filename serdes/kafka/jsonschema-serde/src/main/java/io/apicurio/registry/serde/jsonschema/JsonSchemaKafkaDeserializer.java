package io.apicurio.registry.serde.jsonschema;

import com.networknt.schema.JsonSchema;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.resolver.utils.Utils;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.AbstractDeserializer;
import io.apicurio.registry.serde.KafkaDeserializer;
import io.apicurio.registry.serde.headers.MessageTypeSerdeHeaders;
import org.apache.kafka.common.header.Headers;

import java.util.HashMap;
import java.util.Map;

public class JsonSchemaKafkaDeserializer<T> extends KafkaDeserializer<JsonSchema, T> {

    private MessageTypeSerdeHeaders serdeHeaders;

    public JsonSchemaKafkaDeserializer() {
        super(new JsonSchemaDeserializer<>());
    }

    public JsonSchemaKafkaDeserializer(RegistryClient client) {
        super(new JsonSchemaDeserializer<>(client));
    }

    public JsonSchemaKafkaDeserializer(SchemaResolver<JsonSchema, T> schemaResolver) {
        super(new JsonSchemaDeserializer<>(schemaResolver));
    }

    public JsonSchemaKafkaDeserializer(RegistryClient client, SchemaResolver<JsonSchema, T> schemaResolver) {
        super(new JsonSchemaDeserializer<>(client, schemaResolver));
    }

    public JsonSchemaKafkaDeserializer(RegistryClient client,
            ArtifactReferenceResolverStrategy<JsonSchema, T> strategy,
            SchemaResolver<JsonSchema, T> schemaResolver) {
        super(new JsonSchemaDeserializer<>(client, schemaResolver, strategy));
    }

    public JsonSchemaKafkaDeserializer(AbstractDeserializer<JsonSchema, T> delegatedDeserializer) {
        super(delegatedDeserializer);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs, isKey);
        this.serdeHeaders = new MessageTypeSerdeHeaders(new HashMap<>(configs), isKey);
    }

    @Override
    public T deserialize(String topic, Headers headers, byte[] data) {
        if (headers != null
                && ((JsonSchemaDeserializer<T>) delegatedDeserializer).getSpecificReturnClass() == null) {
            String javaType = serdeHeaders.getMessageType(headers);
            ((JsonSchemaDeserializer<T>) delegatedDeserializer)
                    .setSpecificReturnClass(javaType == null ? null : Utils.loadClass(javaType));
        }

        return super.deserialize(topic, headers, data);
    }
}
