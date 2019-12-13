/*
 * Copyright 2019 Red Hat
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

import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.utils.serde.strategy.ArtifactIdStrategy;
import io.apicurio.registry.utils.serde.strategy.FindBySchemaIdStrategy;
import io.apicurio.registry.utils.serde.strategy.GlobalIdStrategy;
import io.apicurio.registry.utils.serde.strategy.TopicIdStrategy;

import java.util.Map;
import java.util.Objects;

/**
 * @author Ales Justin
 */
public abstract class AbstractKafkaStrategyAwareSerDe<T, S extends AbstractKafkaStrategyAwareSerDe<T, S>> extends AbstractKafkaSerDe<S> {
    public static final String REGISTRY_ARTIFACT_ID_STRATEGY_CONFIG_PARAM = "apicurio.registry.artifact-id";
    public static final String REGISTRY_GLOBAL_ID_STRATEGY_CONFIG_PARAM = "apicurio.registry.global-id";

    private ArtifactIdStrategy<T> artifactIdStrategy;
    private GlobalIdStrategy<T> globalIdStrategy;

    private boolean key; // do we handle key or value with this ser/de?

    public AbstractKafkaStrategyAwareSerDe() {
        this(null);
    }

    public AbstractKafkaStrategyAwareSerDe(RegistryService client) {
        this(client, new TopicIdStrategy<>(), new FindBySchemaIdStrategy<>());
    }

    public AbstractKafkaStrategyAwareSerDe(
        RegistryService client,
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

    protected boolean isKey() {
        return key;
    }

    public S setKey(boolean key) {
        this.key = key;
        return self();
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
        configure(configs);

        Object ais = configs.get(REGISTRY_ARTIFACT_ID_STRATEGY_CONFIG_PARAM);
        instantiate(ArtifactIdStrategy.class, ais, this::setArtifactIdStrategy);

        Object gis = configs.get(REGISTRY_GLOBAL_ID_STRATEGY_CONFIG_PARAM);
        instantiate(GlobalIdStrategy.class, gis, this::setGlobalIdStrategy);

        key = isKey;
    }
}
