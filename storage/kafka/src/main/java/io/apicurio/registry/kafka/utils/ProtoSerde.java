package io.apicurio.registry.kafka.utils;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;

import java.io.UncheckedIOException;
import java.util.Objects;

public class ProtoSerde<M extends MessageLite> extends SelfSerde<M> {

    public static <M extends MessageLite> ProtoSerde<M> parsedWith(Parser<M> parser) {
        return new ProtoSerde<>(parser);
    }

    private final Parser<M> parser;

    protected ProtoSerde(Parser<M> parser) {
        this.parser = Objects.requireNonNull(parser);
    }

    @Override
    public M deserialize(String topic, byte[] data) {
        if (data == null) return null;
        try {
            return parser.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public byte[] serialize(String topic, M message) {
        return message == null ? null : message.toByteArray();
    }
}
