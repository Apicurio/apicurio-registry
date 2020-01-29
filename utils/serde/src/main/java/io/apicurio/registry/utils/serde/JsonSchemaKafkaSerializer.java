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

package io.apicurio.registry.utils.serde;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worldturner.medeia.api.StringSchemaSource;
import com.worldturner.medeia.api.jackson.MedeiaJacksonApi;
import com.worldturner.medeia.schema.validation.SchemaValidator;

import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.serde.strategy.FindLatestIdStrategy;
import io.apicurio.registry.utils.serde.strategy.GlobalIdStrategy;
import io.apicurio.registry.utils.serde.util.Utils;

/**
 * An implementation of the Kafka Serializer for JSON Schema use-cases. This serializer assumes that the
 * user's application needs to serialize a Java Bean to JSON data using Jackson. In addition to standard
 * serialization of the bean, this implementation can also optionally validate it against a JSON schema.
 * 
 * @author eric.wittmann@gmail.com
 */
public class JsonSchemaKafkaSerializer<T>
        extends AbstractKafkaStrategyAwareSerDe<SchemaValidator, JsonSchemaKafkaSerializer<T>>
        implements Serializer<T> {
    
    private static MedeiaJacksonApi api = new MedeiaJacksonApi();
    private static ObjectMapper mapper = new ObjectMapper();
    private static GlobalIdStrategy<SchemaValidator> latestVersionStrategy = new FindLatestIdStrategy<>();
    
    private boolean validationEnabled = false;
    private SchemaCache<SchemaValidator> schemaCache;

    /**
     * Constructor.
     */
    public JsonSchemaKafkaSerializer() {
    }

    /**
     * Constructor.
     * @param client
     * @param validationEnabled
     */
    public JsonSchemaKafkaSerializer(RegistryService client, boolean validationEnabled) {
        super(client);
        
        this.validationEnabled = validationEnabled;
        this.schemaCache = new SchemaCache<SchemaValidator>(getClient()) {
            @Override
            protected SchemaValidator toSchema(Response response) {
                String schema = response.readEntity(String.class);
                return api.loadSchema(new StringSchemaSource(schema));
            }
        };

    }

    /**
     * @see org.apache.kafka.common.serialization.Serializer#configure(java.util.Map, boolean)
     */
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs, isKey);
        
        Object ve = configs.get(JsonSchemaSerDeConstants.REGISTRY_JSON_SCHEMA_VALIDATION_ENABLED);
        this.validationEnabled = Utils.isTrue(ve);
    }
    
    /**
     * @see org.apache.kafka.common.serialization.Serializer#serialize(java.lang.String, java.lang.Object)
     */
    @Override
    public byte[] serialize(String topic, T data) {
        // Headers are required when sending data using this serdes impl
        throw new UnsupportedOperationException();
    }
    
    /**
     * @see org.apache.kafka.common.serialization.Serializer#serialize(java.lang.String, org.apache.kafka.common.header.Headers, java.lang.Object)
     */
    @Override
    public byte[] serialize(String topic, Headers headers, T data) {
        if (data == null) {
            return new byte[0];
        }

        // Now serialize the data
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            JsonGenerator generator = mapper.getFactory().createGenerator(baos);
            if (validationEnabled) {
                String artifactId = getArtifactId(topic, data);
                Long globalId = getArtifactVersionGlobalId(artifactId, topic, data);
                addSchemaHeaders(headers, artifactId, globalId);

                SchemaValidator schemaValidator = schemaCache.getSchema(globalId);
                generator = api.decorateJsonGenerator(schemaValidator, generator);
            }
            addTypeHeaders(headers, data);

            mapper.writeValue(generator, data);
            
            return baos.toByteArray();
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }

    /**
     * Figure out the artifact ID from the topic name and data.
     * @param topic
     * @param data
     */
    protected String getArtifactId(String topic, T data) {
        // Note - for JSON Schema, we don't yet have the schema so we pass null to the strategy.
        return getArtifactIdStrategy().artifactId(topic, isKey(), null);
    }

    /**
     * Gets the global id of the schema to use for validation.
     * @param artifactId
     * @param topic
     * @param data
     */
    protected Long getArtifactVersionGlobalId(String artifactId, String topic, T data) {
        if (getGlobalIdStrategy() == null) {
            return latestVersionStrategy.findId(getClient(), artifactId, ArtifactType.JSON, null);
        }
        // Note - for JSON Schema, we don't yet have the schema so we pass null to the strategy.
        return getGlobalIdStrategy().findId(getClient(), artifactId, ArtifactType.JSON, null);
    }

    /**
     * Adds appropriate information to the Headers so that the deserializer can function properly.
     * @param headers
     * @param artifactId
     * @param globalId
     */
    protected void addSchemaHeaders(Headers headers, String artifactId, Long globalId) {
        if (globalId != null) {
            ByteBuffer buff = ByteBuffer.allocate(8);
            buff.putLong(globalId.longValue());
            headers.add(JsonSchemaSerDeConstants.HEADER_GLOBAL_ID, buff.array());
        } else {
            headers.add(JsonSchemaSerDeConstants.HEADER_ARTIFACT_ID, IoUtil.toBytes(artifactId));
        }
    }

    /**
     * Adds appropriate information to the Headers so that the deserializer can function properly.
     * @param headers
     * @param data
     */
    protected void addTypeHeaders(Headers headers, T data) {
        headers.add(JsonSchemaSerDeConstants.HEADER_MSG_TYPE, IoUtil.toBytes(data.getClass().getName()));
    }

}
