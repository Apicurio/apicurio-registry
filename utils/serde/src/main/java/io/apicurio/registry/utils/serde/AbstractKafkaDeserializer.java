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

package io.apicurio.registry.utils.serde;

import io.apicurio.registry.client.RegistryService;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

import java.nio.ByteBuffer;
import java.util.Map;
import javax.ws.rs.core.Response;

/**
 * @author Ales Justin
 */
public abstract class AbstractKafkaDeserializer<T, U> extends AbstractKafkaSerDe<T> implements Deserializer<U> {
    public AbstractKafkaDeserializer() {
    }

    public AbstractKafkaDeserializer(RegistryService client) {
        super(client);
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        configure(configs);
    }

    private ByteBuffer getByteBuffer(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        if (buffer.get() != MAGIC_BYTE) {
            throw new SerializationException("Unknown magic byte!");
        }
        return buffer;
    }

    protected abstract T toSchema(Response response);

    protected abstract U readData(T schema, ByteBuffer buffer, int start, int length);

    @Override
    public U deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }

        ByteBuffer buffer = getByteBuffer(data);
        long id = buffer.getLong();
        Response artifactResponse = getClient().getArtifact(id);
        Response.StatusType statusInfo = artifactResponse.getStatusInfo();
        if (statusInfo.getStatusCode() != 200) {
            throw new IllegalStateException(String.format(
                "Error [%s] retrieving schema: %s",
                statusInfo.getReasonPhrase(),
                id)
            );
        }
        T schema = toSchema(artifactResponse);
        int length = buffer.limit() - 1 - idSize;
        int start = buffer.position() + buffer.arrayOffset();
        return readData(schema, buffer, start, length);
    }
}
