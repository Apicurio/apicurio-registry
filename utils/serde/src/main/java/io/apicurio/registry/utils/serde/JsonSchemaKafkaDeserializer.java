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

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.worldturner.medeia.schema.validation.SchemaValidator;

import io.apicurio.registry.client.RegistryRestClient;

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
    public JsonSchemaKafkaDeserializer(RegistryRestClient client, Boolean validationEnabled) {
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
                Long globalId = headerUtils.getGlobalId(headers);
                
                // If no globalId is provided, check the alternative - which is to check for artifactId and 
                // (optionally) version.  If these are found, then convert that info to globalId.
                if (globalId == null) {
                    String artifactId = headerUtils.getArtifactId(headers);
                    Integer version = headerUtils.getVersion(headers);
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
     * Gets the message type from the headers.  Throws if not found.
     *
     * @param headers the headers
     */
    @SuppressWarnings("unchecked")
    protected Class<T> getMessageType(Headers headers) {
        String msgTypeName = headerUtils.getMessageType(headers);
        
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

}
