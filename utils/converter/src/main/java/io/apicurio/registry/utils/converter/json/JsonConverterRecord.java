package io.apicurio.registry.utils.converter.json;

import io.apicurio.registry.serde.data.KafkaSerdeRecord;

public class JsonConverterRecord<T> extends KafkaSerdeRecord<T> {

    /**
     * Constructor.
     * 
     * @param metadata
     * @param payload
     */
    public JsonConverterRecord(JsonConverterMetadata metadata, T payload) {
        super(metadata, payload);
    }

    /**
     * @see io.apicurio.registry.serde.data.KafkaSerdeRecord#metadata()
     */
    @Override
    public JsonConverterMetadata metadata() {
        return (JsonConverterMetadata) super.metadata();
    }

}
