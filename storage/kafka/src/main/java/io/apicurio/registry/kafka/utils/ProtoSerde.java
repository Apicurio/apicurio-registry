/*
 * Copyright 2019 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
