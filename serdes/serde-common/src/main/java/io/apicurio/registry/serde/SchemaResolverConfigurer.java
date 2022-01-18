/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.serde;

import java.util.Map;
import java.util.Objects;

import io.apicurio.registry.resolver.DefaultSchemaResolver;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.SchemaResolverConfig;
import io.apicurio.registry.resolver.utils.Utils;
import io.apicurio.registry.rest.client.RegistryClient;

/**
 * Base class for any kind of serializer/deserializer that depends on {@link SchemaResolver}
 *
 * @author Fabian Martinez
 */
public class SchemaResolverConfigurer<T, U> {

    protected SchemaResolver<T, U> schemaResolver;

    /**
     * Constructor.
     */
    public SchemaResolverConfigurer() {
        super();
    }

    public SchemaResolverConfigurer(RegistryClient client) {
        this(client, new DefaultSchemaResolver<>());
    }

    public SchemaResolverConfigurer(SchemaResolver<T, U> schemaResolver) {
        this(null, schemaResolver);
    }

    public SchemaResolverConfigurer(
        RegistryClient client,
        SchemaResolver<T, U> schemaResolver
    ) {
        this();
        setSchemaResolver(schemaResolver);
        getSchemaResolver().setClient(client);
    }

    public SchemaResolver<T, U> getSchemaResolver() {
        return schemaResolver;
    }

    public void setSchemaResolver(SchemaResolver<T, U> schemaResolver) {
        this.schemaResolver = Objects.requireNonNull(schemaResolver);
    }

    protected void configure(Map<String, Object> configs, boolean isKey, SchemaParser<T, U> schemaParser) {
        Objects.requireNonNull(configs);
        Objects.requireNonNull(schemaParser);
        if (this.schemaResolver == null) {
            Object sr = configs.get(SerdeConfig.SCHEMA_RESOLVER);
            if (null == sr) {
                this.setSchemaResolver(new DefaultSchemaResolver<>());
            } else {
                Utils.instantiate(SchemaResolver.class, sr, this::setSchemaResolver);
            }
        }
        // enforce default artifactResolverStrategy for kafka apps
        if (!configs.containsKey(SchemaResolverConfig.ARTIFACT_RESOLVER_STRATEGY)) {
            configs.put(SchemaResolverConfig.ARTIFACT_RESOLVER_STRATEGY, SerdeConfig.ARTIFACT_RESOLVER_STRATEGY_DEFAULT);
        }
        // isKey is passed via config property
        configs.put(SerdeConfig.IS_KEY, isKey);
        getSchemaResolver().configure(configs, schemaParser);
    }

}