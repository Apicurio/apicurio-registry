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
import java.nio.charset.StandardCharsets;
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

/**
 * An implementation of the Kafka Serializer for JSON Schema use-cases. This serializer assumes that the
 * user's application needs to serialize a Java Bean to JSON data using Jackson. In addition to standard
 * serialization of the bean, this implementation can also optionally validate it against a JSON schema.
 * 
 * @author eric.wittmann@gmail.com
 */
public class JsonSchemaKafkaSerializer<T> extends AbstractKafkaSerDe<JsonSchemaKafkaSerializer<T>> implements Serializer<T> {
    
    public static final String REGISTRY_JSON_SCHEMA_SERIALIZER_VALIDATION_ENABLED = "apicurio.registry.serdes.json-schema.validation-enabled";

    private static MedeiaJacksonApi api = new MedeiaJacksonApi();
    private static ObjectMapper mapper = new ObjectMapper();
    
    private boolean validationEnabled = false;

    /**
     * Constructor.
     */
    public JsonSchemaKafkaSerializer() {
    }

    /**
     * Constructor.
     * @param client
     */
    public JsonSchemaKafkaSerializer(RegistryService client) {
        super(client);
    }

    /**
     * @see org.apache.kafka.common.serialization.Serializer#configure(java.util.Map, boolean)
     */
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs);
        
        Object ve = configs.get(REGISTRY_JSON_SCHEMA_SERIALIZER_VALIDATION_ENABLED);
        this.validationEnabled = ve != null && ("true".equals(ve) || ve.equals(Boolean.TRUE));
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
                Integer version = getArtifactVersion(artifactId, topic, data);
                addSchemaHeaders(headers, artifactId, version);

                String schema = loadSchema(artifactId, version);
                SchemaValidator schemaValidator = api.loadSchema(new StringSchemaSource(schema));
                // TODO cache the SchemaValidator - keyed on artifactId+version
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
    private String getArtifactId(String topic, T data) {
        // TODO support other options - other impls use a strategy for this
        return topic;
    }

    /**
     * Figure out which version of the artifact to use, specifically.  If this returns
     * null then the latest version will be used.
     * @param artifactId
     * @param topic
     * @param data
     */
    private Integer getArtifactVersion(String artifactId, String topic, T data) {
        // TODO how can we know what artifact version we need?  passed in via config?
        return null;
    }

    /**
     * Adds appropriate information to the Headers so that the deserializer can function properly.
     * @param headers
     * @param artifactId
     * @param version
     */
    private void addSchemaHeaders(Headers headers, String artifactId, Integer version) {
        headers.add(JsonSchemaSerDeConstants.HEADER_ARTIFACT_ID, artifactId.getBytes(StandardCharsets.UTF_8));
        if (version != null) {
            ByteBuffer buff = ByteBuffer.allocate(4);
            buff.putInt(version.intValue());
            headers.add(JsonSchemaSerDeConstants.HEADER_VERSION, buff.array());
        }
    }

    /**
     * Adds appropriate information to the Headers so that the deserializer can function properly.
     * @param headers
     * @param data
     */
    private void addTypeHeaders(Headers headers, T data) {
        headers.add(JsonSchemaSerDeConstants.HEADER_MSG_TYPE, data.getClass().getName().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Gets the schema from the registry.
     * @param artifactId
     * @param version
     */
    private String loadSchema(String artifactId, Integer version) {
        String schema;
        Response artifact;
        
        if (version == null) {
            artifact = getClient().getLatestArtifact(artifactId);
        } else {
            artifact = getClient().getArtifactVersion(version, artifactId);
        }
        
        if (artifact.getStatus() != 200) {
            throw new RuntimeException("Failed to get schema from registry: [" + artifact.getStatus() + "] " + 
                    artifact.getStatusInfo().getReasonPhrase());
        }
        
        schema = artifact.readEntity(String.class);
        return schema;
    }

}
