package io.apicurio.registry.serde.jsonschema;

import com.networknt.schema.JsonSchema;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.resolver.utils.Utils;
import io.apicurio.registry.serde.kafka.KafkaDeserializer;
import io.apicurio.registry.serde.kafka.headers.MessageTypeSerdeHeaders;
import org.apache.kafka.common.header.Headers;

import java.util.HashMap;
import java.util.Map;

public class JsonSchemaKafkaDeserializer<T> extends KafkaDeserializer<JsonSchema, T> {

    private MessageTypeSerdeHeaders serdeHeaders;

    public JsonSchemaKafkaDeserializer() {
        super(new JsonSchemaDeserializer<>());
    }

    public JsonSchemaKafkaDeserializer(RegistryClientFacade clientFacade) {
        super(new JsonSchemaDeserializer<>(clientFacade));
    }

    public JsonSchemaKafkaDeserializer(SchemaResolver<JsonSchema, T> schemaResolver) {
        super(new JsonSchemaDeserializer<>(schemaResolver));
    }

    public JsonSchemaKafkaDeserializer(RegistryClientFacade clientFacade, SchemaResolver<JsonSchema, T> schemaResolver) {
        super(new JsonSchemaDeserializer<>(clientFacade, schemaResolver));
    }

    public JsonSchemaKafkaDeserializer(RegistryClientFacade clientFacade,
                                       ArtifactReferenceResolverStrategy<JsonSchema, T> strategy,
                                       SchemaResolver<JsonSchema, T> schemaResolver) {
        super(new JsonSchemaDeserializer<>(clientFacade, schemaResolver, strategy));
    }

    @SuppressWarnings("unchecked")
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs, isKey);
        this.serdeHeaders = new MessageTypeSerdeHeaders(new HashMap<>(configs), isKey);
    }

    @Override
    public T deserialize(String topic, Headers headers, byte[] data) {
        if (serdeHeaders != null && headers != null
                && ((JsonSchemaDeserializer<T>) delegatedDeserializer).getSpecificReturnClass() == null) {
            String javaType = serdeHeaders.getMessageType(headers);
            ((JsonSchemaDeserializer<T>) delegatedDeserializer)
                    .setSpecificReturnClass(javaType == null ? null : Utils.loadClass(javaType));
        }

        return super.deserialize(topic, headers, data);
    }
}
