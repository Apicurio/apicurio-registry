/*
 * Copyright 2020 Red Hat
 * Copyright 2020 IBM
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

package io.apicurio.registry.serdes;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serdes.utils.Utils;

/**
 * @author Ales Justin
 */
public abstract class AbstractKafkaDeserializer<T, U, S extends AbstractKafkaDeserializer<T, U, S>> extends AbstractKafkaSerDe<S> implements Deserializer<U>, SchemaMapper<T, U> {

    private SchemaResolver<T, U> schemaResolver;

    public AbstractKafkaDeserializer() {
        //empty
    }

    public AbstractKafkaDeserializer(RegistryClient client) {
        this(client, new DefaultSchemaResolver<>());
    }

    public AbstractKafkaDeserializer(RegistryClient client, SchemaResolver<T, U> schemaResolver) {
        setSchemaResolver(schemaResolver);
        getSchemaResolver().setClient(client);
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs, isKey);

        //TODO artifact id strategy is not used in deserialization
//        Object ais = configs.get(SerdeConfigKeys.ARTIFACT_ID_STRATEGY);
//        instantiate(ArtifactIdStrategy.class, ais, this::setArtifactIdStrategy);

        Object sr = configs.get(SerdeConfigKeys.SCHEMA_RESOLVER);
        if (null == sr) {
            sr = new DefaultSchemaResolver<>();
        }
        Utils.instantiate(SchemaResolver.class, sr, this::setSchemaResolver);
        getSchemaResolver().configure(configs, isKey, this);
    }

    protected SchemaResolver<T, U> getSchemaResolver() {
        return schemaResolver;
    }

    public S setSchemaResolver(SchemaResolver<T, U> schemaResolver) {
        this.schemaResolver = Objects.requireNonNull(schemaResolver);
        return self();
    }

    @Override
    public void reset() {
        //TODO schema resolver reset
        super.reset();
    }

//    protected abstract T toSchema(InputStream schemaData);
//
    protected abstract U readData(T schema, ByteBuffer buffer, int start, int length);
//
    protected abstract U readData(Headers headers, T schema, ByteBuffer buffer, int start, int length);

    @Override
    public U deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }

        ByteBuffer buffer = getByteBuffer(data);
        long id = getIdHandler().readId(buffer);

        SchemaLookupResult<T> schema = getSchemaResolver().resolveSchemaByGlobalId(id);


//        T schema = getCache().getSchema(id);
        int length = buffer.limit() - 1 - getIdHandler().idSize();
        int start = buffer.position() + buffer.arrayOffset();

        return readData(schema.getParsedSchema(), buffer, start, length);
    }

    @Override
    public U deserialize(String topic, Headers headers, byte[] data) {
        if (data == null) {
            return null;
        }
        // check if data contains the magic byte
        if (data[0] == MAGIC_BYTE){
            return deserialize(topic, data);
        } else {
            T schema = null;
            Long id = headerUtils.getGlobalId(headers);
            if (id == null) {
                String artifactId = headerUtils.getArtifactId(headers);
                Integer version = headerUtils.getVersion(headers);
                schema = getSchemaResolver().resolveSchemaByArtifactId("TODO", artifactId, version).getParsedSchema();
//                id = toGlobalId(artifactId, version);
            } else {
                schema = getSchemaResolver().resolveSchemaByGlobalId(id).getParsedSchema();
            }
//            T schema = getCache().getSchema(id);
            ByteBuffer buffer = ByteBuffer.wrap(data);
            int length = buffer.limit();
            int start = buffer.position();
            return readData(headers, schema, buffer, start, length);
        }
    }

}
