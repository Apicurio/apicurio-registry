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

import java.util.Map;
import java.util.Objects;

import io.apicurio.registry.client.RegistryRestClient;
import io.apicurio.registry.utils.serde.strategy.ArtifactIdStrategy;
import io.apicurio.registry.utils.serde.strategy.FindBySchemaIdStrategy;
import io.apicurio.registry.utils.serde.strategy.GlobalIdStrategy;
import io.apicurio.registry.utils.serde.strategy.TopicIdStrategy;

/**
 * @author Ales Justin
 */
public abstract class AbstractKafkaStrategyAwareSerDe<T, S extends AbstractKafkaStrategyAwareSerDe<T, S>> extends AbstractKafkaSerDe<S> {

    private ArtifactIdStrategy<T> artifactIdStrategy;
    private GlobalIdStrategy<T> globalIdStrategy;

    public AbstractKafkaStrategyAwareSerDe() {
        this(null);
    }

    public AbstractKafkaStrategyAwareSerDe(RegistryRestClient client) {
        this(client, new TopicIdStrategy<>(), new FindBySchemaIdStrategy<>());
    }

    public AbstractKafkaStrategyAwareSerDe(
        RegistryRestClient client,
        ArtifactIdStrategy<T> artifactIdStrategy,
        GlobalIdStrategy<T> globalIdStrategy
    ) {
        super(client);
        setArtifactIdStrategy(artifactIdStrategy);
        setGlobalIdStrategy(globalIdStrategy);
    }

    protected ArtifactIdStrategy<T> getArtifactIdStrategy() {
        return artifactIdStrategy;
    }

    protected GlobalIdStrategy<T> getGlobalIdStrategy() {
        return globalIdStrategy;
    }

    public S setArtifactIdStrategy(ArtifactIdStrategy<T> artifactIdStrategy) {
        this.artifactIdStrategy = Objects.requireNonNull(artifactIdStrategy);
        return self();
    }

    public S setGlobalIdStrategy(GlobalIdStrategy<T> globalIdStrategy) {
        this.globalIdStrategy = Objects.requireNonNull(globalIdStrategy);
        return self();
    }

    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs, isKey);

        Object ais = configs.get(SerdeConfig.ARTIFACT_ID_STRATEGY);
        instantiate(ArtifactIdStrategy.class, ais, this::setArtifactIdStrategy);

        Object gis = configs.get(SerdeConfig.GLOBAL_ID_STRATEGY);
        instantiate(GlobalIdStrategy.class, gis, this::setGlobalIdStrategy);
    }
}
