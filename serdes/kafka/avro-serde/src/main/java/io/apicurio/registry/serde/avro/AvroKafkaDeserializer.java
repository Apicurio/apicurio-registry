package io.apicurio.registry.serde.avro;

import org.apache.avro.Schema;
import org.apache.kafka.common.header.Headers;

import java.util.Map;

import io.apicurio.registry.serde.kafka.KafkaDeserializer;

public class AvroKafkaDeserializer<U> extends KafkaDeserializer<Schema, U> {

    private AvroSerdeHeaders avroHeaders;

    public AvroKafkaDeserializer() {
        super(AvroDeserializer::new);
    }

    @Override
    protected void initialize(Map<String, ?> configs, boolean isKey) {
        avroHeaders = new AvroSerdeHeaders(isKey);
    }

    @Override
    public U deserialize(String topic, Headers headers, byte[] data) {
        AvroEncoding encoding = null;
        if (headers != null) {
            String encodingHeader = avroHeaders.getEncoding(headers);
            if (encodingHeader != null) {
                encoding = AvroEncoding.valueOf(encodingHeader);
            }
        }
        if (encoding != null) {
            ((AvroDeserializer<U>) delegatedDeserializer).setEncoding(encoding);
        }

        return super.deserialize(topic, headers, data);
    }
}
