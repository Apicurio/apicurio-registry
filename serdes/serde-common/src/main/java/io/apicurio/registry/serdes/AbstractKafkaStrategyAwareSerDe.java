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

package io.apicurio.registry.serdes;

import java.util.Map;
import java.util.Objects;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serdes.strategy.ArtifactIdStrategy;
import io.apicurio.registry.serdes.utils.Utils;

/**
 * @author Ales Justin
 */
public abstract class AbstractKafkaStrategyAwareSerDe<T, U, S extends AbstractKafkaStrategyAwareSerDe<T, U, S>> extends AbstractKafkaSerDe<S> implements SchemaMapper<T, U> {

    private SchemaResolver<T, U> schemaResolver;

    public AbstractKafkaStrategyAwareSerDe() {
        //empty
    }

    public AbstractKafkaStrategyAwareSerDe(RegistryClient client) {
        this(client, null, new DefaultSchemaResolver<>());
    }

    public AbstractKafkaStrategyAwareSerDe(ArtifactIdStrategy<T> artifactIdStrategy) {
        this(null, artifactIdStrategy, new DefaultSchemaResolver<>());
    }

    public AbstractKafkaStrategyAwareSerDe(SchemaResolver<T, U> schemaResolver) {
        this(null, null, schemaResolver);
    }

    public AbstractKafkaStrategyAwareSerDe(
        RegistryClient client,
        ArtifactIdStrategy<T> artifactIdStrategy,
        SchemaResolver<T, U> schemaResolver
    ) {
        setSchemaResolver(schemaResolver);
        getSchemaResolver().setClient(client);
        getSchemaResolver().setArtifactIdStrategy(artifactIdStrategy);
    }

    protected SchemaResolver<T, U> getSchemaResolver() {
        return schemaResolver;
    }

    public S setSchemaResolver(SchemaResolver<T, U> schemaResolver) {
        this.schemaResolver = Objects.requireNonNull(schemaResolver);
        return self();
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs, isKey);

        if (this.schemaResolver == null) {
            Object sr = configs.get(SerdeConfigKeys.SCHEMA_RESOLVER);
            if (null == sr) {
                this.setSchemaResolver(new DefaultSchemaResolver<>());
            } else {
                Utils.instantiate(SchemaResolver.class, sr, this::setSchemaResolver);
            }
        }
        getSchemaResolver().configure(configs, isKey, this);
    }

}
