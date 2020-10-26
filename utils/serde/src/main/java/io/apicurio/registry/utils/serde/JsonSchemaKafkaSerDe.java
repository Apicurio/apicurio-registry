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

import java.io.InputStream;
import java.util.Map;

import org.apache.kafka.common.serialization.Serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worldturner.medeia.api.StringSchemaSource;
import com.worldturner.medeia.api.jackson.MedeiaJacksonApi;
import com.worldturner.medeia.schema.validation.SchemaValidator;

import io.apicurio.registry.client.RegistryRestClient;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.serde.util.HeaderUtils;
import io.apicurio.registry.utils.serde.util.Utils;

/**
 * @author Ales Justin
 */
public class JsonSchemaKafkaSerDe<S extends JsonSchemaKafkaSerDe<S>> extends AbstractKafkaStrategyAwareSerDe<SchemaValidator, S> {

    protected static MedeiaJacksonApi api = new MedeiaJacksonApi();
    protected static ObjectMapper mapper = new ObjectMapper();

    private Boolean validationEnabled;
    private SchemaCache<SchemaValidator> schemaCache;
    protected HeaderUtils headerUtils;

    /**
     * Constructor.
     */
    public JsonSchemaKafkaSerDe() {
        this(null, null);
    }

    /**
     * Constructor.
     *
     * @param client            the registry client
     * @param validationEnabled validation enabled flag
     */
    public JsonSchemaKafkaSerDe(RegistryRestClient client, Boolean validationEnabled) {
        super(client);
        this.validationEnabled = validationEnabled;
    }

    public boolean isValidationEnabled() {
        return validationEnabled != null && validationEnabled;
    }

    public S setValidationEnabled(boolean validationEnabled) {
        this.validationEnabled = validationEnabled;
        return self();
    }

    /**
     * @see Serializer#configure(Map, boolean)
     */
    @SuppressWarnings("unchecked")
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs, isKey);

        if (validationEnabled == null) {
            Object ve = configs.get(SerdeConfig.VALIDATION_ENABLED);
            this.validationEnabled = Utils.isTrue(ve);
        }
        
        headerUtils = new HeaderUtils((Map<String, Object>) configs, isKey);

        // TODO allow the schema to be configured here
    }

    protected SchemaCache<SchemaValidator> getSchemaCache() {
        if (schemaCache == null) {
            schemaCache = new SchemaCache<SchemaValidator>(getClient()) {
                @Override
                protected SchemaValidator toSchema(InputStream schemaData) {
                    String schema = IoUtil.toString(schemaData);
                    return api.loadSchema(new StringSchemaSource(schema));
                }
            };
        }
        return schemaCache;
    }
}
