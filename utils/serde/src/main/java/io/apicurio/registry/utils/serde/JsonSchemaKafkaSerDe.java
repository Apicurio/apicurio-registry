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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worldturner.medeia.api.StringSchemaSource;
import com.worldturner.medeia.api.jackson.MedeiaJacksonApi;
import com.worldturner.medeia.schema.validation.SchemaValidator;
import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.utils.serde.util.Utils;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;
import javax.ws.rs.core.Response;

/**
 * @author Ales Justin
 */
public class JsonSchemaKafkaSerDe<S extends JsonSchemaKafkaSerDe<S>> extends AbstractKafkaStrategyAwareSerDe<SchemaValidator, S> {

    protected static MedeiaJacksonApi api = new MedeiaJacksonApi();
    protected static ObjectMapper mapper = new ObjectMapper();

    private Boolean validationEnabled;
    private SchemaCache<SchemaValidator> schemaCache;

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
    public JsonSchemaKafkaSerDe(RegistryService client, Boolean validationEnabled) {
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
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs, isKey);

        if (validationEnabled == null) {
            Object ve = configs.get(JsonSchemaSerDeConstants.REGISTRY_JSON_SCHEMA_VALIDATION_ENABLED);
            this.validationEnabled = Utils.isTrue(ve);
        }
    }

    protected SchemaCache<SchemaValidator> getSchemaCache() {
        if (schemaCache == null) {
            schemaCache = new SchemaCache<SchemaValidator>(getClient()) {
                @Override
                protected SchemaValidator toSchema(Response response) {
                    String schema = response.readEntity(String.class);
                    return api.loadSchema(new StringSchemaSource(schema));
                }
            };
        }
        return schemaCache;
    }
}
