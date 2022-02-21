/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.storage.impl.kafkasql.serde;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.apache.kafka.common.serialization.Serializer;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.apicurio.registry.storage.impl.kafkasql.values.ContentValue;
import io.apicurio.registry.storage.impl.kafkasql.values.MessageValue;

/**
 * Responsible for serializing the message key to bytes.
 * @author eric.wittmann@gmail.com
 */
public class KafkaSqlValueSerializer implements Serializer<MessageValue> {

    private static final ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.setSerializationInclusion(Include.NON_NULL);
    }

    /**
     * @see org.apache.kafka.common.serialization.Serializer#serialize(java.lang.String, java.lang.Object)
     */
    @Override
    public byte[] serialize(String topic, MessageValue messageValue) {
        if (messageValue == null) {
            return null;
        }

        if (messageValue.getType() == MessageType.Content) {
            return this.serializeContent(topic, (ContentValue) messageValue);
        }
        try (UnsynchronizedByteArrayOutputStream out = new UnsynchronizedByteArrayOutputStream()) {
            out.write(ByteBuffer.allocate(1).put(messageValue.getType().getOrd()).array());
            mapper.writeValue(out, messageValue);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Special case for serializing a {@link ContentValue}.
     * @param topic
     * @param contentValue
     */
    private byte[] serializeContent(String topic, ContentValue contentValue) {
        try (UnsynchronizedByteArrayOutputStream out = new UnsynchronizedByteArrayOutputStream()) {
            out.write(ByteBuffer.allocate(1).put(contentValue.getType().getOrd()).array());
            out.write(ByteBuffer.allocate(1).put(contentValue.getAction().getOrd()).array());
            if (contentValue.getCanonicalHash() != null) {
                byte[] bytes = contentValue.getCanonicalHash().getBytes(StandardCharsets.UTF_8);
                out.write(ByteBuffer.allocate(4).putInt(bytes.length).array());
                out.write(ByteBuffer.allocate(bytes.length).put(bytes).array());
            } else {
                out.write(ByteBuffer.allocate(4).putInt(0).array());
            }

            if (contentValue.getContent() != null) {
                byte[] contentBytes = contentValue.getContent().bytes();
                out.write(ByteBuffer.allocate(4).putInt(contentBytes.length).array());
                out.write(contentBytes);
            } else {
                out.write(ByteBuffer.allocate(4).putInt(0).array());
            }

            //set references bytes and count
            if (null != contentValue.getSerializedReferences()) {
                byte[] bytes = contentValue.getSerializedReferences().getBytes(StandardCharsets.UTF_8);
                out.write(ByteBuffer.allocate(4).putInt(bytes.length).array());
                out.write(ByteBuffer.allocate(bytes.length).put(bytes).array());
            } else {
                out.write(ByteBuffer.allocate(4).putInt(0).array());
            }

            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
