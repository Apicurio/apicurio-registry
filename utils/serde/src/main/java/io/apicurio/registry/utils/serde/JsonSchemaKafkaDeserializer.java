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
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.VersionMetaData;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.serde.util.Utils;

/**
 * @author eric.wittmann@gmail.com
 */
public class JsonSchemaKafkaDeserializer<T> extends AbstractKafkaSerDe<JsonSchemaKafkaDeserializer<T>> implements Deserializer<T> {

    private static MedeiaJacksonApi api = new MedeiaJacksonApi();
    private static ObjectMapper mapper = new ObjectMapper();
    
    private boolean validationEnabled = false;
    private SchemaCache<SchemaValidator> schemaCache;

    /**
     * Constructor.
     */
    public JsonSchemaKafkaDeserializer() {
        this(null, false);
    }

    /**
     * Constructor.
     * @param client
     */
    public JsonSchemaKafkaDeserializer(RegistryService client, boolean validationEnabled) {
        super(client);
        
        this.validationEnabled = validationEnabled;
    }

    /**
     * Lazy getter for the schema cache.
     */
    protected SchemaCache<SchemaValidator> getSchemaCache() {
        if (schemaCache == null) {
            this.schemaCache = new SchemaCache<SchemaValidator>(getClient()) {
                @Override
                protected SchemaValidator toSchema(Response response) {
                    String schema = response.readEntity(String.class);
                    return api.loadSchema(new StringSchemaSource(schema));
                }
            };
        }
        return schemaCache;
    }
    
    /**
     * @see org.apache.kafka.common.serialization.Deserializer#configure(java.util.Map, boolean)
     */
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs);

        Object ve = configs.get(JsonSchemaSerDeConstants.REGISTRY_JSON_SCHEMA_VALIDATION_ENABLED);
        this.validationEnabled = Utils.isTrue(ve);
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
                Long globalId = getGlobalId(headers);
                
                // If no globalId is provided, check the alternative - which is to check for artifactId and 
                // (optionally) version.  If these are found, then convert that info to globalId.
                if (globalId == null) {
                    String artifactId = getArtifactId(headers);
                    Integer version = getVersion(headers);
                    globalId = toGlobalId(artifactId, version);
                }
                
                SchemaValidator schema = getSchemaCache().getSchema(globalId);
                parser = api.decorateJsonParser(schema, parser);
            }
            
            Class<T> messageType = getMessageType(headers);

            return mapper.readValue(parser, messageType);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Gets the global id from the headers.  Returns null if not found.
     * @param headers
     */
    protected Long getGlobalId(Headers headers) {
        Header header = headers.lastHeader(JsonSchemaSerDeConstants.HEADER_GLOBAL_ID);
        if (header == null) {
            return null;
        }
        return ByteBuffer.wrap(header.value()).getLong();
    }

    /**
     * Gets the artifact id from the headers.  Throws if not found.
     * @param headers
     */
    protected String getArtifactId(Headers headers) {
        Header header = headers.lastHeader(JsonSchemaSerDeConstants.HEADER_ARTIFACT_ID);
        if (header == null) {
            throw new RuntimeException("ArtifactId not found in headers.");
        }
        return IoUtil.toString(header.value());
    }

    /**
     * Gets the artifact version from the headers.  Returns null if not found.
     * @param headers
     */
    protected Integer getVersion(Headers headers) {
        Header header = headers.lastHeader(JsonSchemaSerDeConstants.HEADER_VERSION);
        if (header == null) {
            return null;
        }
        return ByteBuffer.wrap(header.value()).getInt();
    }

    /**
     * Gets the message type from the headers.  Throws if not found.
     * @param headers
     */
    protected Class<T> getMessageType(Headers headers) {
        Header header = headers.lastHeader(JsonSchemaSerDeConstants.HEADER_MSG_TYPE);
        if (header == null) {
            throw new RuntimeException("Message Type not found in headers.");
        }
        String msgTypeName = IoUtil.toString(header.value());
        
        try {
            return (Class<T>) Thread.currentThread().getContextClassLoader().loadClass(msgTypeName);
        } catch (ClassNotFoundException e) {}
        try {
            return (Class<T>) Class.forName(msgTypeName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Converts an artifact id and version to a global id by querying the registry.  If anything goes wrong, 
     * throws an approprate exception.
     * @param artifactId
     * @param version
     */
    protected Long toGlobalId(String artifactId, Integer version) {
        if (version == null) {
            ArtifactMetaData amd = getClient().getArtifactMetaData(artifactId);
            return amd.getGlobalId();
        } else {
            VersionMetaData vmd = getClient().getArtifactVersionMetaData(version, artifactId);
            return vmd.getGlobalId();
        }
    }

}
