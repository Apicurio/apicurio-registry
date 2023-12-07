package io.apicurio.registry.serde.data;

import io.apicurio.registry.resolver.data.Record;

public class KafkaSerdeRecord<T> implements Record<T> {

    private KafkaSerdeMetadata metadata;
    private T payload;

    public KafkaSerdeRecord(KafkaSerdeMetadata metadata, T payload) {
        this.metadata = metadata;
        this.payload = payload;
    }

    /**
     * @see io.apicurio.registry.resolver.data.Record#metadata()
     */
    @Override
    public KafkaSerdeMetadata metadata() {
        return metadata;
    }

    /**
     * @see io.apicurio.registry.resolver.data.Record#payload()
     */
    @Override
    public T payload() {
        return payload;
    }

}
