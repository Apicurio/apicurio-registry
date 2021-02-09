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

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serdes.strategy.ArtifactIdStrategy;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

/**
 * @author Ales Justin
 */
public abstract class AbstractKafkaSerializer<T, U, S extends AbstractKafkaSerializer<T, U, S>> extends AbstractKafkaStrategyAwareSerDe<T, U, S> implements Serializer<U> {

    public AbstractKafkaSerializer() {
        super();
    }

    public AbstractKafkaSerializer(RegistryClient client) {
        super(client);
    }

    public AbstractKafkaSerializer(ArtifactIdStrategy<T> artifactIdStrategy) {
        super(artifactIdStrategy);
    }

    public AbstractKafkaSerializer(SchemaResolver<T, U> schemaResolver) {
        super(schemaResolver);
    }

    public AbstractKafkaSerializer(RegistryClient client, ArtifactIdStrategy<T> artifactIdStrategy,
            SchemaResolver<T, U> schemaResolver) {
        super(client, artifactIdStrategy, schemaResolver);
    }

    protected abstract void serializeData(T schema, U data, OutputStream out) throws IOException;

    protected abstract void serializeData(Headers headers, T schema, U data, ByteArrayOutputStream out) throws IOException;

    @Override
    public byte[] serialize(String topic, U data) {
        return serialize(topic, null, data);
    }

    @Override
    public byte[] serialize(String topic, Headers headers, U data) {
        // just return null
        if (data == null) {
            return null;
        }
        try {

            //set headers inside the resolver?
            SchemaLookupResult<T> schema = getSchemaResolver().resolveSchema(topic, headers, data);

//            T schema = toSchema(data);
//            String artifactId = getArtifactIdStrategy().artifactId(topic, isKey(), schema);
//            long id = getGlobalIdStrategy().findId(getClient(), artifactId, artifactType(), schema, getCache());
//            // Note: we need to retry this fetch to account for the possibility that schema was just concurrently added
//            // to the registry, but the registry is not yet ready to serve it.  This is due to some registry
//            // storages being asynchronous.
//            // GlobalId strategies that create or update the schema or those which find global id by schema content
//            // already populate the cache properly, so no retry should be required.
//            schema = retry(() -> getCache().getSchema(id), 5); // use registry's schema!

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (headerUtils != null) {
                headerUtils.addSchemaHeaders(headers, schema.getArtifactId(), schema.getGlobalId());
                serializeData(headers, schema.getParsedSchema(), data, out);
            } else {
                out.write(MAGIC_BYTE);
                getIdHandler().writeId(schema.getGlobalId(), out);
                serializeData(schema.getParsedSchema(), data, out);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
