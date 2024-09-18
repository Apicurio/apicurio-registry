package io.apicurio.registry.serde.data;

import io.apicurio.registry.resolver.data.Record;

public class SerdeRecord<T> implements Record<T> {

    private final SerdeMetadata metadata;
    private final T payload;

    public SerdeRecord(SerdeMetadata metadata, T payload) {
        this.metadata = metadata;
        this.payload = payload;
    }

    /**
     * @see io.apicurio.registry.resolver.data.Record#metadata()
     */
    @Override
    public SerdeMetadata metadata() {
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
