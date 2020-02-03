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

import com.fasterxml.jackson.core.JsonParser;
import com.worldturner.medeia.schema.validation.SchemaValidator;
import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.VersionMetaData;
import io.apicurio.registry.utils.IoUtil;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

/**
 * @author eric.wittmann@gmail.com
 * @author Ales Justin
 */
public class JsonSchemaKafkaDeserializer<T> extends JsonSchemaKafkaSerDe<JsonSchemaKafkaDeserializer<T>> implements Deserializer<T> {

    /**
     * Constructor.
     */
    public JsonSchemaKafkaDeserializer() {
        this(null, null);
    }

    /**
     * Constructor.
     */
    public JsonSchemaKafkaDeserializer(RegistryService client, Boolean validationEnabled) {
        super(client, validationEnabled);
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
            if (isValidationEnabled()) {
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
     *
     * @param headers the headers
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
     *
     * @param headers the headers
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
     *
     * @param headers the headers
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
     *
     * @param headers the headers
     */
    @SuppressWarnings("unchecked")
    protected Class<T> getMessageType(Headers headers) {
        Header header = headers.lastHeader(JsonSchemaSerDeConstants.HEADER_MSG_TYPE);
        if (header == null) {
            throw new RuntimeException("Message Type not found in headers.");
        }
        String msgTypeName = IoUtil.toString(header.value());
        
        try {
            return (Class<T>) Thread.currentThread().getContextClassLoader().loadClass(msgTypeName);
        } catch (ClassNotFoundException ignored) {
        }
        try {
            return (Class<T>) Class.forName(msgTypeName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Converts an artifact id and version to a global id by querying the registry.  If anything goes wrong, 
     * throws an appropriate exception.
     *
     * @param artifactId artifact id
     * @param version the artifact version
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
