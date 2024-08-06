package io.apicurio.registry.utils.converter.json;

import io.apicurio.registry.serde.data.KafkaSerdeMetadata;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.connect.data.Schema;

public class JsonConverterMetadata extends KafkaSerdeMetadata {

    private Schema schema;

    /**
     * Constructor.
     * 
     * @param topic
     * @param isKey
     * @param headers
     */
    public JsonConverterMetadata(String topic, boolean isKey, Headers headers, Schema schema) {
        super(topic, isKey, headers);
        this.schema = schema;
    }

    /**
     * @return the schema
     */
    public Schema getSchema() {
        return schema;
    }

}
