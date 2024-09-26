package io.apicurio.registry.serde.jsonschema;

import com.networknt.schema.JsonSchema;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.serde.AbstractSerializer;
import io.apicurio.registry.serde.KafkaSerializer;
import io.apicurio.registry.serde.headers.MessageTypeSerdeHeaders;
import org.apache.kafka.common.header.Headers;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * An implementation of the Kafka Serializer for JSON Schema use-cases. This serializer assumes that the
 * user's application needs to serialize a Java Bean to JSON data using Jackson. In addition to standard
 * serialization of the bean, this implementation can also optionally validate it against a JSON schema.
 */
public class JsonSchemaKafkaSerializer<T> extends KafkaSerializer<JsonSchema, T> {

    private MessageTypeSerdeHeaders serdeHeaders;

    public JsonSchemaKafkaSerializer(AbstractSerializer<JsonSchema, T> delegatedSerializer) {
        super(delegatedSerializer);
    }

    /**
     * @see KafkaSerializer#configure(java.util.Map, boolean)
     */
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs, isKey);
        serdeHeaders = new MessageTypeSerdeHeaders(new HashMap<>(configs), isKey);
    }

    /**
     * @param validationEnabled the validationEnabled to set
     */
    public void setValidationEnabled(Boolean validationEnabled) {
        ((JsonSchemaSerializer<T>) delegatedSerializer).setValidationEnabled(validationEnabled);
    }

    /**
     * @see KafkaSerializer#serializeData(org.apache.kafka.common.header.Headers,
     *      io.apicurio.registry.resolver.ParsedSchema, java.lang.Object, java.io.OutputStream)
     */
    @Override
    protected void serializeData(Headers headers, ParsedSchema<JsonSchema> schema, T data, OutputStream out)
            throws IOException {

        if (headers != null) {
            serdeHeaders.addMessageTypeHeader(headers, data.getClass().getName());
        }

        delegatedSerializer.serializeData(schema, data, out);
    }
}
