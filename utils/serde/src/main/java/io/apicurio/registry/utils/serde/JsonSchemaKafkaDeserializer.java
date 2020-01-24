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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worldturner.medeia.api.StringSchemaSource;
import com.worldturner.medeia.api.jackson.MedeiaJacksonApi;
import com.worldturner.medeia.schema.validation.SchemaValidator;

import io.apicurio.registry.client.RegistryService;

/**
 * @author eric.wittmann@gmail.com
 */
public class JsonSchemaKafkaDeserializer<T> extends AbstractKafkaSerDe<JsonSchemaKafkaDeserializer<T>> implements Deserializer<T> {

    public static final String REGISTRY_JSON_SCHEMA_DESERIALIZER_VALIDATION_ENABLED = "apicurio.registry.serdes.json-schema.validation-enabled";

    private static MedeiaJacksonApi api = new MedeiaJacksonApi();
    private static ObjectMapper mapper = new ObjectMapper();
    
    private boolean validationEnabled = false;

    /**
     * Constructor.
     */
    public JsonSchemaKafkaDeserializer() {
        this(null);
    }

    /**
     * Constructor.
     * @param client
     */
    public JsonSchemaKafkaDeserializer(RegistryService client) {
        super(client);
    }

    /**
     * @see org.apache.kafka.common.serialization.Deserializer#configure(java.util.Map, boolean)
     */
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs);

        Object ve = configs.get(REGISTRY_JSON_SCHEMA_DESERIALIZER_VALIDATION_ENABLED);
        this.validationEnabled = ve != null && ("true".equals(ve) || ve.equals(Boolean.TRUE));
    }
    
    /**
     * @see org.apache.kafka.common.serialization.Deserializer#deserialize(java.lang.String, byte[])
     */
    @Override
    public T deserialize(String topic, byte[] data) {
        throw new UnsupportedOperationException();
    }
    
    /**
     * @see org.apache.kafka.common.serialization.Deserializer#deserialize(java.lang.String, org.apache.kafka.common.header.Headers, byte[])
     */
    @Override
    public T deserialize(String topic, Headers headers, byte[] data) {
        if (data == null) {
            return null;
        }
        
        try {
            JsonParser parser = mapper.getFactory().createParser(data);
            if (validationEnabled) {
                String artifactId = getArtifactId(headers);
                Integer version = getVersion(headers);
                String schemaContent = loadSchema(artifactId, version);
                // TODO cache the SchemaValidator instance - keyed on artifactId+version
                SchemaValidator schema = api.loadSchema(new StringSchemaSource(schemaContent));
                parser = api.decorateJsonParser(schema, parser);
            }
            
            Class<T> messageType = getMessageType(headers);

            return mapper.readValue(parser, messageType);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Gets the artifact id from the headers.  Throws if not found.
     * @param headers
     */
    private String getArtifactId(Headers headers) {
        Header header = headers.lastHeader(JsonSchemaSerDeConstants.HEADER_ARTIFACT_ID);
        if (header == null) {
            throw new RuntimeException("ArtifactId not found in headers.");
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    /**
     * Gets the artifact version from the headers.  Returns null if not found.
     * @param headers
     */
    private Integer getVersion(Headers headers) {
        Header header = headers.lastHeader(JsonSchemaSerDeConstants.HEADER_VERSION);
        if (header == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.put(header.value());
        return buffer.getInt();
    }

    /**
     * Loads the schema from the registry.
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

    /**
     * Gets the message type from the headers.  Throws if not found.
     * @param headers
     */
    @SuppressWarnings("unchecked")
    private Class<T> getMessageType(Headers headers) {
        Header header = headers.lastHeader(JsonSchemaSerDeConstants.HEADER_MSG_TYPE);
        if (header == null) {
            throw new RuntimeException("Message Type not found in headers.");
        }
        String msgTypeName = new String(header.value(), StandardCharsets.UTF_8);
        
        try {
            return (Class<T>) Thread.currentThread().getContextClassLoader().loadClass(msgTypeName);
        } catch (ClassNotFoundException e) {}
        try {
            return (Class<T>) Class.forName(msgTypeName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}
