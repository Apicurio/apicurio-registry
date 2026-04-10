package io.apicurio.schema.validation.protobuf;

import com.google.protobuf.Message;
import io.apicurio.registry.resolver.data.Record;

public class ProtobufRecord implements Record<Message> {

    private final Message payload;
    private final ProtobufMetadata metadata;

    public ProtobufRecord(Message payload, ProtobufMetadata metadata) {
        this.payload = payload;
        this.metadata = metadata;
    }

    @Override
    public ProtobufMetadata metadata() {
        return this.metadata;
    }

    @Override
    public Message payload() {
        return this.payload;
    }
}