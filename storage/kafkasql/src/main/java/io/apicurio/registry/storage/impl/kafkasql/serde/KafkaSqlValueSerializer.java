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
            out.write(ByteBuffer.allocate(1).put((byte) messageValue.getType().getOrd()).array());
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
            // Byte 0 is the message type
            out.write(ByteBuffer.allocate(1).put((byte) contentValue.getType().getOrd()).array());
            // Byte 1 is the action type
            out.write(ByteBuffer.allocate(1).put((byte) contentValue.getAction().getOrd()).array());
            // Byte 2 is the artifact type
            out.write(ByteBuffer.allocate(1).put(ArtifactTypeOrdUtil.artifactTypeToOrd(contentValue.getArtifactType())).array());
            // The rest of the bytes is the content
            out.write(contentValue.getContent().bytes());
            
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
