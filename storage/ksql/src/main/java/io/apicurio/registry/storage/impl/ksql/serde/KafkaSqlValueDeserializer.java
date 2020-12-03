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

package io.apicurio.registry.storage.impl.ksql.serde;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;

import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.kafka.common.serialization.Deserializer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.impl.ksql.keys.MessageType;
import io.apicurio.registry.storage.impl.ksql.values.ActionType;
import io.apicurio.registry.storage.impl.ksql.values.ContentValue;
import io.apicurio.registry.storage.impl.ksql.values.MessageTypeToValueClass;
import io.apicurio.registry.storage.impl.ksql.values.MessageValue;
import io.apicurio.registry.types.ArtifactType;

/**
 * Kafka deserializer responsible for deserializing the value of a KSQL Kafka message.
 * @author eric.wittmann@gmail.com
 */
public class KafkaSqlValueDeserializer implements Deserializer<MessageValue> {

    private static final ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, true);
    }
    private static final ActionType[] actionTypes = ActionType.values();
    private static final ArtifactType[] artifactTypes = ArtifactType.values();

    /**
     * @see org.apache.kafka.common.serialization.Deserializer#deserialize(java.lang.String, byte[])
     */
    @Override
    public MessageValue deserialize(String topic, byte[] data) {
        byte msgTypeOrdinal = data[0];
        if (msgTypeOrdinal == MessageType.Content.ordinal()) {
            return this.deserializeContent(topic, data);
        }
        try {
            Class<? extends MessageValue> keyClass = MessageTypeToValueClass.ordinalToValue(msgTypeOrdinal);
            UnsynchronizedByteArrayInputStream in = new UnsynchronizedByteArrayInputStream(data, 1);
            MessageValue key = mapper.readValue(in, keyClass);
            return key;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Special case deserialize of a {@link ContentValue} value.
     * @param topic
     * @param data
     */
    private ContentValue deserializeContent(String topic, byte[] data) {
        byte actionOrdinal = data[1];
        byte artifactTypeOrdinal = data[2];
        byte[] contentBytes = Arrays.copyOfRange(data, 3, data.length);
        
        ActionType action = actionTypes[actionOrdinal];
        ArtifactType artifactType = artifactTypes[artifactTypeOrdinal];
        ContentHandle contentHandle = ContentHandle.create(contentBytes);
        
        return ContentValue.create(action, artifactType, contentHandle);
    }

}
